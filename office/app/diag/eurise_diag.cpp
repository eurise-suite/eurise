/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * Diagnosi di Eurise Office, solo nelle build di prova (office/app/eurise_diag.py la aggiunge a libandroidapp).
 *
 * Quando il motore si chiude da solo, Android scrive solo «Fatal signal 6» e, sotto la traduzione ARM
 * dell'emulatore, una pila senza nomi. Questo file scrive nel logcat (etichetta EuriseDiag):
 *  - il tipo e il messaggio dell'eccezione C++ che nessuno ha preso (std::terminate);
 *  - la pila al momento del segnale, come libreria + scostamento, seguendo i frame pointer di arm64;
 *  - quello che il motore scrive su stderr e stdout (LibreOffice e coolwsd ci mettono gli errori fatali).
 * Gli scostamenti si traducono in nomi di funzione con llvm-symbolizer e le librerie non ripulite
 * (artefatto office-simboli di office-app.yml, usato da office-prova.yml).
 */

#include <android/log.h>
#include <cxxabi.h>
#include <dlfcn.h>
#include <pthread.h>
#include <signal.h>
#include <ucontext.h>
#include <unistd.h>

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <typeinfo>

namespace
{
constexpr const char* TAG = "EuriseDiag";

void logFrame(int index, uintptr_t pc)
{
    Dl_info info{};
    if (dladdr(reinterpret_cast<void*>(pc), &info) != 0 && info.dli_fname != nullptr)
    {
        const uintptr_t offset = pc - reinterpret_cast<uintptr_t>(info.dli_fbase);
        __android_log_print(ANDROID_LOG_FATAL, TAG, "pila #%02d pc %010zx %s %s", index,
                            static_cast<size_t>(offset), info.dli_fname,
                            info.dli_sname != nullptr ? info.dli_sname : "");
    }
    else
        __android_log_print(ANDROID_LOG_FATAL, TAG, "pila #%02d pc %p ?", index,
                            reinterpret_cast<void*>(pc));
}

/// Segue la catena dei frame pointer (x29) a partire dal contesto del segnale.
void logStack(const ucontext_t* context)
{
#if defined(__aarch64__)
    const uintptr_t pc = context->uc_mcontext.pc;
    const uintptr_t lr = context->uc_mcontext.regs[30];
    uintptr_t fp = context->uc_mcontext.regs[29];
    logFrame(0, pc);
    logFrame(1, lr);
    for (int i = 2; i < 80 && fp != 0 && (fp & 0xf) == 0; ++i)
    {
        const auto* frame = reinterpret_cast<const uintptr_t*>(fp);
        const uintptr_t next = frame[0];
        const uintptr_t ret = frame[1];
        if (ret == 0)
            break;
        logFrame(i, ret);
        if (next <= fp || next - fp > (8u << 20))
            break;
        fp = next;
    }
#else
    (void)context;
    __android_log_write(ANDROID_LOG_FATAL, TAG, "pila: solo arm64");
#endif
}

void onFatalSignal(int signal, siginfo_t* info, void* context)
{
    char name[32] = {};
    pthread_getname_np(pthread_self(), name, sizeof(name));
    __android_log_print(ANDROID_LOG_FATAL, TAG, "segnale %d (codice %d) nel thread %d [%s]", signal,
                        info != nullptr ? info->si_code : 0, gettid(), name);
    logStack(static_cast<const ucontext_t*>(context));
    // Poi il gestore di sistema, che scrive la tombstone come sempre.
    struct sigaction defaults{};
    defaults.sa_handler = SIG_DFL;
    sigaction(signal, &defaults, nullptr);
    raise(signal);
}

const char* demangled(const char* mangled, char* buffer, size_t size)
{
    int status = 0;
    char* text = abi::__cxa_demangle(mangled, nullptr, nullptr, &status);
    if (status != 0 || text == nullptr)
        return mangled;
    snprintf(buffer, size, "%s", text);
    free(text);
    return buffer;
}

void onTerminate()
{
    char type[256] = {};
    if (std::exception_ptr current = std::current_exception())
    {
        try
        {
            std::rethrow_exception(current);
        }
        catch (const std::exception& e)
        {
            __android_log_print(ANDROID_LOG_FATAL, TAG, "eccezione non presa: %s: %s",
                                demangled(typeid(e).name(), type, sizeof(type)), e.what());
        }
        catch (...)
        {
            const std::type_info* info = abi::__cxa_current_exception_type();
            __android_log_print(ANDROID_LOG_FATAL, TAG, "eccezione non presa di tipo %s",
                                info != nullptr ? demangled(info->name(), type, sizeof(type)) : "?");
        }
    }
    else
        __android_log_write(ANDROID_LOG_FATAL, TAG, "std::terminate senza eccezione");
    abort();
}

int g_pipe[2] = { -1, -1 };

void* forwardOutput(void*)
{
    pthread_setname_np(pthread_self(), "eurise_stderr");
    FILE* in = fdopen(g_pipe[0], "r");
    if (in == nullptr)
        return nullptr;
    char line[1024];
    while (fgets(line, sizeof(line), in) != nullptr)
    {
        const size_t length = strlen(line);
        if (length > 0 && line[length - 1] == '\n')
            line[length - 1] = '\0';
        __android_log_write(ANDROID_LOG_WARN, "EuriseStderr", line);
    }
    return nullptr;
}

__attribute__((constructor)) void installDiagnostics()
{
    std::set_terminate(onTerminate);

    struct sigaction action{};
    action.sa_sigaction = onFatalSignal;
    action.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&action.sa_mask);
    for (int signal : { SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGFPE })
        sigaction(signal, &action, nullptr);

    if (pipe(g_pipe) == 0)
    {
        setvbuf(stderr, nullptr, _IONBF, 0);
        setvbuf(stdout, nullptr, _IOLBF, 0);
        dup2(g_pipe[1], STDERR_FILENO);
        dup2(g_pipe[1], STDOUT_FILENO);
        pthread_t thread;
        if (pthread_create(&thread, nullptr, forwardOutput, nullptr) == 0)
            pthread_detach(thread);
    }
    __android_log_write(ANDROID_LOG_INFO, TAG, "diagnosi attiva");
}
} // namespace

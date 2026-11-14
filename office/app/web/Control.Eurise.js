/* -*- js-indent-level: 8 -*- */
/*
 * Eurise Office: scheda «Suite» del nastro, «Cerca un comando» (Ctrl K) e pulsante Suite sul telefono
 * (tavole OffTelefono, OffCerca, OffTablet, OffComputer); con un PDF, le schede Annota e Pagine e «Prima di
 * condividere» (tavole OffPdf, OffPdfPagine, OffPdfCondividi). File di Eurise aggiunto all'interfaccia di Collabora Online
 * (MPL 2.0) da office/app/eurise_web.py, subito dopo Control.NotebookbarBuilder.js.
 *
 * I comandi della Suite non toccano la rete: chiedono al guscio dell'app (Android: EuriseSuite.java) di passare il
 * testo scelto alle altre app Eurise sul telefono. Messaggi verso il guscio: «EURISE {json}» con postMobileMessage;
 * risposte: window.euriseOffice.reply({id, ...}). Lo stesso protocollo servirà al guscio per Linux.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

/* global app _UNO JSDialog MobileBottomBar */

(function () {
	'use strict';

	var IT = String(String.locale || navigator.language || 'it').toLowerCase().indexOf('it') === 0;
	function t(it, en) { return IT ? it : en; }

	var SUITE_TAB = 'Suite';

	// --- Ponte con il guscio dell'app -------------------------------------------------------------------------

	var pending = {};
	var nextId = 1;

	function toShell(message, onReply) {
		var id = nextId++;
		message.id = id;
		if (onReply) pending[id] = onReply;
		if (window.ThisIsAMobileApp && window.postMobileMessage) {
			window.postMobileMessage('EURISE ' + JSON.stringify(message));
		} else if (window.parent && window.parent !== window) {
			// Sul computer (guscio futuro) o in una pagina che ospita l'editor.
			window.parent.postMessage(JSON.stringify({ MessageId: 'Eurise', Values: message }), '*');
		} else {
			delete pending[id];
			notice(t('Funziona dentro l\'app Eurise Office.', 'Works inside the Eurise Office app.'));
		}
	}

	function reply(message) {
		if (typeof message === 'string') message = JSON.parse(message);
		var cb = pending[message.id];
		delete pending[message.id];
		if (message.notice) notice(message.notice);
		if (cb) cb(message);
	}

	function notice(text) {
		var el = document.getElementById('eurise-notice');
		if (!el) {
			el = document.createElement('div');
			el.id = 'eurise-notice';
			document.body.appendChild(el);
		}
		el.textContent = text;
		el.classList.add('visible');
		clearTimeout(notice.timer);
		notice.timer = setTimeout(function () { el.classList.remove('visible'); }, 3200);
	}

	// --- Documento: testo scelto, nome, inserimento -----------------------------------------------------------

	/** Chiede al motore il testo scelto (arriva con textselectioncontent) e lo passa a cb; '' se non c'è. */
	function withSelection(cb) {
		var clip = app.map && app.map._clip;
		if (!clip || !app.socket) { cb(''); return; }
		var done = false;
		var origHtml = clip.setTextSelectionHTML;
		function finish() {
			if (done) return;
			done = true;
			clip.setTextSelectionHTML = origHtml;
			var text = clip._selectionType === 'text' ? (clip._selectionPlainTextContent || '') : '';
			cb(String(text).replace(/\r\n?/g, '\n').trim());
		}
		clip.setTextSelectionHTML = function () {
			origHtml.apply(clip, arguments);
			finish();
		};
		app.socket.sendMessage('gettextselection mimetype=text/html,text/plain;charset=utf-8');
		setTimeout(finish, 1500);
	}

	function documentName() {
		var wopi = app.map && app.map['wopi'];
		return (wopi && wopi.BaseFileName) || '';
	}

	/** Scrive il testo nel punto del cursore; se c'è una selezione la sostituisce (come quando si digita). */
	function insertText(text) {
		if (!text || !app.socket) return;
		app.socket.sendMessage('textinput id=' + app.map.getWinId() + ' text=' + encodeURIComponent(text));
	}

	// --- Comandi della Suite ----------------------------------------------------------------------------------

	function sendSelection(action, empty) {
		withSelection(function (text) {
			if (!text && empty) { notice(empty); return; }
			toShell({ action: action, text: text, title: documentName() });
		});
	}

	var actions = {
		'eurise-cerca': function () { openSearch(); },
		'eurise-detta': function () {
			toShell({ action: 'detta' }, function (r) { if (r.text) insertText(r.text); });
		},
		'eurise-traduci': function () {
			withSelection(function (text) {
				if (!text) { notice(t('Scegli prima il testo da tradurre.', 'Select the text to translate first.')); return; }
				toShell({ action: 'lingue' }, function (r) {
					if (!r.languages || !r.languages.length) return;
					chooseLanguages(r.languages, function (from, to) {
						toShell({ action: 'traduci', text: text, from: from, to: to }, function (res) {
							if (res.text) insertText(res.text);
						});
					});
				});
			});
		},
		'eurise-dafare': function () { sendSelection('dafare', null); },
		'eurise-evento': function () { sendSelection('evento', null); },
		'eurise-note': function () { sendSelection('note', null); },
		'eurise-viaggio': function () {
			sendSelection('viaggio', t('Scegli le righe delle spese da portare in Viaggio.', 'Select the expense rows first.'));
		},
		'eurise-grafico': function () { app.map.sendUnoCommand('.uno:InsertObjectChart'); },
	};

	/** Scelta della coppia di lingue tra quelle scaricate in Eurise Leggi. */
	function chooseLanguages(languages, done) {
		var names = {};
		try {
			var dn = new Intl.DisplayNames([IT ? 'it' : 'en'], { type: 'language' });
			languages.forEach(function (l) { names[l] = dn.of(l); });
		} catch (e) {
			languages.forEach(function (l) { names[l] = l; });
		}
		var box = overlay('eurise-lingue');
		box.innerHTML = '';
		var title = document.createElement('div');
		title.className = 'eurise-title';
		title.textContent = t('Traduci', 'Translate');
		box.appendChild(title);
		function select(label, initial) {
			var row = document.createElement('label');
			row.className = 'eurise-row';
			row.textContent = label;
			var s = document.createElement('select');
			languages.forEach(function (l) {
				var o = document.createElement('option');
				o.value = l;
				o.textContent = names[l] || l;
				s.appendChild(o);
			});
			s.value = initial;
			row.appendChild(s);
			box.appendChild(row);
			return s;
		}
		var mine = IT ? 'it' : 'en';
		var other = languages.filter(function (l) { return l !== mine; })[0] || languages[0];
		var from = select(t('Da', 'From'), languages.indexOf(other) >= 0 ? other : languages[0]);
		var to = select(t('A', 'To'), languages.indexOf(mine) >= 0 ? mine : languages[0]);
		var note = document.createElement('div');
		note.className = 'eurise-sub';
		note.textContent = t('Sul telefono con Eurise Leggi: il testo non esce.', 'On the phone with Eurise Read: the text stays here.');
		box.appendChild(note);
		var go = document.createElement('button');
		go.className = 'eurise-button';
		go.textContent = t('Traduci e sostituisci', 'Translate and replace');
		go.onclick = function () { closeOverlay(); done(from.value, to.value); };
		box.appendChild(go);
	}

	// Ogni azione eurise-* passa da qui; le altre vanno al dispatcher di Collabora com'erano.
	var Dispatcher = app.definitions && app.definitions['dispatcher'];
	if (Dispatcher) {
		var originalDispatch = Dispatcher.prototype.dispatch;
		Dispatcher.prototype.dispatch = function (action, data) {
			if (typeof action === 'string' && actions[action]) {
				actions[action](data);
				return;
			}
			return originalDispatch.call(this, action, data);
		};
	}

	// --- Scheda Suite del nastro (tablet e computer) ----------------------------------------------------------

	function bigButton(id, it, en) {
		return {
			'id': id,
			'type': 'bigcustomtoolitem',
			'text': t(it, en),
			'command': id,
			'icon': 'lc_' + id.replace('-', '_') + '.svg',
			'accessibility': { focusBack: true, combination: id.substring(7, 9).toUpperCase(), de: null }
		};
	}

	function suiteContent(docType) {
		var group = [
			bigButton('eurise-detta', 'Detta', 'Dictate'),
			bigButton('eurise-traduci', 'Traduci', 'Translate'),
			bigButton('eurise-dafare', 'Da fare', 'To-do'),
			bigButton('eurise-evento', 'Evento', 'Event'),
		];
		if (docType === 'spreadsheet') {
			group.push(bigButton('eurise-viaggio', 'Viaggio', 'Trip'));
			group.push(bigButton('eurise-grafico', 'Grafico', 'Chart'));
		} else {
			group.push(bigButton('eurise-note', 'Note', 'Notes'));
		}
		return [
			{
				'type': 'overflowgroup',
				'id': 'suite-search',
				'name': t('Cerca', 'Search'),
				'children': [bigButton('eurise-cerca', 'Cerca un comando', 'Search a command')]
			},
			{ type: 'separator', id: 'suite-search-break', orientation: 'vertical' },
			{
				'type': 'overflowgroup',
				'id': 'suite-suite',
				'class': 'eurise-suite-group',
				'name': t('★ Suite', '★ Suite'),
				'children': group
			}
		];
	}

	function addSuiteTab(Klass) {
		if (!Klass || !Klass.prototype) return;
		var proto = Klass.prototype;
		// Solo dove la classe ha le sue schede: chi le eredita riceve già la Suite dalla classe madre.
		if (!Object.prototype.hasOwnProperty.call(proto, 'getTabs')) return;
		var getTabs = proto.getTabs;
		var getTabsJSON = proto.getTabsJSON;
		proto.getTabs = function () {
			var tabs = getTabs.call(this).slice();
			tabs.push({
				'text': t('Suite', 'Suite'),
				'id': SUITE_TAB + '-tab-label',
				'name': SUITE_TAB,
				'accessibility': { focusBack: true, combination: 'U', de: null }
			});
			return tabs;
		};
		proto.getTabsJSON = function () {
			var json = getTabsJSON.call(this).slice();
			json.push(this.getTabPage(SUITE_TAB, suiteContent(this.map && this.map.getDocType ? this.map.getDocType() : 'text')));
			return json;
		};
	}

	// Il contenuto segue il tipo di documento aperto (Viaggio e Grafico nel foglio di calcolo).
	if (window.L && window.L.Control) {
		['NotebookbarWriter', 'NotebookbarCalc', 'NotebookbarImpress', 'NotebookbarDraw'].forEach(function (name) {
			addSuiteTab(window.L.Control[name]);
		});
	}

	// --- Pulsante Suite sul telefono (barra in basso) ---------------------------------------------------------

	if (typeof MobileBottomBar !== 'undefined') {
		var getToolItems = MobileBottomBar.prototype.getToolItems;
		MobileBottomBar.prototype.getToolItems = function () {
			var items = getToolItems.call(this).slice();
			items.unshift({ type: 'customtoolitem', id: 'eurise-suite', command: 'eurise-suite', icon: 'lc_eurise_suite.svg', text: t('Suite', 'Suite') });
			return items;
		};
		actions['eurise-suite'] = function () { openSearch(); };
	}

	// --- Cerca un comando (Ctrl K) ----------------------------------------------------------------------------

	/** Posti dove cercare, come nella Tastiera (TastCerca): tacche 3 resta qui, 2 esce solo la domanda, 1 con l'account. */
	var PLACES = [
		{ section: t('Nella suite', 'In the suite'), id: 'suite', label: t('Cerca in Eurise', 'Search Eurise'), sub: t('documenti, file, calendario, persone · sul telefono', 'documents, files, calendar, people · on the phone'), level: 3 },
		{ section: t('Sul web', 'On the web'), id: 'engine:brave', label: 'Brave Search', sub: t('indice proprio · USA', 'own index · USA'), level: 2 },
		{ id: 'engine:qwant', label: 'Qwant', sub: t('Francia, UE', 'France, EU'), level: 2 },
		{ id: 'engine:ecosia', label: 'Ecosia', sub: t('Germania, UE', 'Germany, EU'), level: 2 },
		{ id: 'engine:ddg', label: 'DuckDuckGo', sub: t('risultati Bing · USA', 'Bing results · USA'), level: 2 },
		{ section: t('Chiedi all\'AI', 'Ask AI'), id: 'ai:device', label: t('Eurise AI sul dispositivo', 'Eurise AI on device'), sub: t('in arrivo', 'coming soon'), level: 3, disabled: true },
		{ id: 'ai:duck', label: 'Duck.ai', sub: t('anonimo, senza account · USA', 'anonymous, no account · USA'), level: 2 },
		{ id: 'ai:lechat', label: 'Le Chat', sub: t('Mistral · Francia, UE', 'Mistral · France, EU'), level: 1 },
	];

	/** Comandi comuni, per quando il nastro non c'è (telefono): etichette tradotte dal motore. */
	var COMMON = ['.uno:Bold', '.uno:Italic', '.uno:Underline', '.uno:InsertTable', '.uno:InsertGraphic',
		'.uno:InsertObjectChart', '.uno:HyperlinkDialog', '.uno:InsertAnnotation', '.uno:InsertPagebreak',
		'.uno:CharmapControl', '.uno:SearchDialog', '.uno:SpellingAndGrammarDialog', '.uno:WordCountDialog',
		'.uno:ExportToPDF', '.uno:Print', '.uno:Undo', '.uno:Redo', '.uno:TrackChanges', '.uno:InsertDateField',
		'.uno:InsertPageNumberField', '.uno:DefaultBullet', '.uno:DefaultNumbering', '.uno:FontDialog',
		'.uno:ParagraphDialog', '.uno:PageDialog'];

	function fold(s) {
		return String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[~_]/g, '');
	}

	/** Tutti i comandi del nastro con il nome della scheda («Inserisci › Tabella»), più i comuni. */
	function collectCommands() {
		var out = [];
		var seen = {};
		function add(label, command, where) {
			label = String(label || '').replace(/~/g, '').trim();
			if (!label || !command || seen[command]) return;
			seen[command] = true;
			out.push({ label: label, command: command, where: where || '' });
		}
		var nb = app.map && app.map.uiManager && app.map.uiManager.notebookbar;
		var impl = nb && (nb.impl || nb);
		if (impl && typeof impl.getTabsJSON === 'function') {
			var names = {};
			(impl.getTabs() || []).forEach(function (tab) { if (tab && tab.name) names[tab.name] = tab.text; });
			impl.getTabsJSON().forEach(function (page) {
				var container = page && page.children && page.children[0];
				if (!container) return;
				var tabName = String(container.id || '').split('-')[0];
				if (tabName === SUITE_TAB) return;
				(function walk(items) {
					(items || []).forEach(function (item) {
						if (!item) return;
						if (item.text && (item.command || item.type === 'bigcustomtoolitem' || item.type === 'customtoolitem'))
							add(item.text, item.command || item.id, names[tabName]);
						if (item.children) walk(item.children);
					});
				})(container.children);
			});
		}
		if (typeof _UNO === 'function') COMMON.forEach(function (c) { add(_UNO(c, app.map.getDocType(), true), c, ''); });
		return out;
	}

	function runCommand(command) {
		closeOverlay();
		if (String(command).indexOf('.uno:') === 0) app.map.sendUnoCommand(command);
		else app.dispatcher.dispatch(command);
	}

	function overlay(id) {
		closeOverlay();
		var shade = document.createElement('div');
		shade.id = 'eurise-overlay';
		shade.onclick = function (e) { if (e.target === shade) closeOverlay(); };
		var box = document.createElement('div');
		box.id = id;
		box.className = 'eurise-panel';
		shade.appendChild(box);
		document.body.appendChild(shade);
		return box;
	}

	function closeOverlay() {
		var shade = document.getElementById('eurise-overlay');
		if (shade) shade.remove();
		if (app.map && app.map.focus) app.map.focus();
	}

	function bars(level) {
		var el = document.createElement('span');
		el.className = 'eurise-bars';
		el.title = level === 3 ? t('Resta qui', 'Stays here') : level === 2 ? t('Esce solo la domanda', 'Only the question leaves') : t('Con il tuo account', 'With your account');
		for (var i = 1; i <= 3; i++) {
			var b = document.createElement('i');
			if (i <= level) b.className = 'on';
			el.appendChild(b);
		}
		return el;
	}

	function openSearch() {
		var commands = collectCommands();
		var box = overlay('eurise-cerca');
		var input = document.createElement('input');
		input.type = 'search';
		input.placeholder = t('Cerca un comando, nel documento, nella suite o sul web', 'Search a command, the suite or the web');
		input.setAttribute('aria-label', t('Cerca un comando', 'Search a command'));
		box.appendChild(input);
		var list = document.createElement('div');
		list.className = 'eurise-list';
		box.appendChild(list);
		var rows = [];
		var active = 0;

		function setActive(i) {
			if (!rows.length) return;
			active = (i + rows.length) % rows.length;
			rows.forEach(function (r, n) { r.el.classList.toggle('active', n === active); });
			rows[active].el.scrollIntoView({ block: 'nearest' });
		}

		function row(label, sub, where, level, run, disabled) {
			var el = document.createElement('div');
			el.className = 'eurise-item' + (disabled ? ' disabled' : '');
			var main = document.createElement('div');
			main.className = 'eurise-label';
			main.textContent = label;
			if (where) {
				var w = document.createElement('span');
				w.className = 'eurise-where';
				w.textContent = where;
				main.appendChild(w);
			}
			el.appendChild(main);
			if (sub) {
				var s = document.createElement('div');
				s.className = 'eurise-sub';
				s.textContent = sub;
				el.appendChild(s);
			}
			if (level) el.appendChild(bars(level));
			list.appendChild(el);
			if (!disabled) {
				var index = rows.length;
				rows.push({ el: el, run: run });
				el.onmouseenter = function () { setActive(index); };
				el.onclick = run;
			}
		}

		function heading(text) {
			var h = document.createElement('div');
			h.className = 'eurise-section';
			h.textContent = text;
			list.appendChild(h);
		}

		function render() {
			var q = input.value.trim();
			var fq = fold(q);
			list.innerHTML = '';
			rows = [];
			var found = commands.filter(function (c) { return !fq || fold(c.label).indexOf(fq) >= 0; });
			found.sort(function (a, b) { return fold(a.label).indexOf(fq) - fold(b.label).indexOf(fq); });
			if (found.length) heading(t('Comandi', 'Commands'));
			found.slice(0, q ? 8 : 6).forEach(function (c) {
				row(c.label, '', c.where, 0, function () { runCommand(c.command); });
			});
			if (q) {
				PLACES.forEach(function (p) {
					if (p.section) heading(p.section);
					row(p.label + ' «' + q + '»', p.sub, '', p.level, function () {
						closeOverlay();
						toShell({ action: 'cerca', where: p.id, query: q });
					}, p.disabled);
				});
			}
			setActive(0);
		}

		input.oninput = render;
		input.onkeydown = function (e) {
			if (e.key === 'ArrowDown') { setActive(active + 1); e.preventDefault(); }
			else if (e.key === 'ArrowUp') { setActive(active - 1); e.preventDefault(); }
			else if (e.key === 'Enter') { if (rows[active]) rows[active].run(); e.preventDefault(); }
			else if (e.key === 'Escape') { closeOverlay(); e.preventDefault(); }
			e.stopPropagation();
		};
		render();
		withSelection(function (text) {
			if (text && !input.value && text.length < 120) { input.value = text; render(); input.select(); }
		});
		setTimeout(function () { input.focus(); }, 0);
	}

	// Ctrl K (Cmd K sul Mac) apre la ricerca; in LibreOffice era il collegamento, che resta tra i comandi.
	document.addEventListener('keydown', function (e) {
		if ((e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && (e.key === 'k' || e.key === 'K')) {
			e.preventDefault();
			e.stopPropagation();
			openSearch();
		}
	}, true);

	// --- PDF: Annota, Pagine, Prima di condividere (tavole OffPdf, OffPdfPagine, OffPdfCondividi) -------------
	//
	// Un PDF si apre come disegno (Draw): il testo del PDF è modificabile, le note sono i commenti, le pagine sono
	// le pagine di Draw. Le schede Annota e Pagine compaiono solo con un PDF; «Prima di condividere» esporta una copia
	// con le opzioni scelte attraverso il guscio, che poi la condivide o la passa a «Salva in…» di Eurise File.

	function isPdf() { return /\.pdf$/i.test(documentName()); }

	function unoButton(command, it, en) {
		return {
			'type': 'bigtoolitem',
			'text': t(it, en),
			'command': command,
			'accessibility': { focusBack: true, combination: null, de: null }
		};
	}

	function pdfAnnotate() {
		return [
			{
				'type': 'overflowgroup',
				'id': 'pdf-annota',
				'name': t('Annota', 'Annotate'),
				'children': [
					bigButton('eurise-evidenzia', 'Evidenzia', 'Highlight'),
					bigButton('eurise-sottolinea', 'Sottolinea', 'Underline'),
					unoButton('.uno:InsertAnnotation', 'Nota', 'Note'),
					bigButton('eurise-penna', 'Penna', 'Pen'),
					unoButton('.uno:Text', 'Testo', 'Text'),
					bigButton('eurise-firma', 'Firma', 'Sign')
				]
			},
			{ type: 'separator', id: 'pdf-annota-break', orientation: 'vertical' },
			{
				'type': 'overflowgroup',
				'id': 'pdf-condividi',
				'name': t('Condividi', 'Share'),
				'children': [bigButton('eurise-condividi', 'Prima di condividere', 'Before sharing')]
			}
		];
	}

	function pdfPages() {
		return [
			{
				'type': 'overflowgroup',
				'id': 'pdf-pagine',
				'name': t('Pagine', 'Pages'),
				'children': [
					unoButton('.uno:InsertPage', 'Nuova', 'New'),
					unoButton('.uno:DuplicatePage', 'Duplica', 'Duplicate'),
					unoButton('.uno:MovePageUp', 'Sposta prima', 'Move up'),
					unoButton('.uno:MovePageDown', 'Sposta dopo', 'Move down'),
					unoButton('.uno:DeletePage', 'Togli', 'Delete')
				]
			}
		];
	}

	var PDF_TABS = [
		{ name: 'EuriseAnnota', it: 'Annota', en: 'Annotate', key: 'A', content: pdfAnnotate },
		{ name: 'EurisePagine', it: 'Pagine', en: 'Pages', key: 'G', content: pdfPages }
	];

	function addPdfTabs(Klass) {
		if (!Klass || !Klass.prototype) return;
		var proto = Klass.prototype;
		var getTabs = proto.getTabs;
		var getTabsJSON = proto.getTabsJSON;
		proto.getTabs = function () {
			var tabs = getTabs.call(this).slice();
			if (!isPdf()) return tabs;
			return PDF_TABS.map(function (tab) {
				return { 'text': t(tab.it, tab.en), 'id': tab.name + '-tab-label', 'name': tab.name, 'accessibility': { focusBack: true, combination: tab.key, de: null } };
			}).concat(tabs);
		};
		proto.getTabsJSON = function () {
			var json = getTabsJSON.call(this).slice();
			if (!isPdf()) return json;
			var self = this;
			return PDF_TABS.map(function (tab) { return self.getTabPage(tab.name, tab.content()); }).concat(json);
		};
	}
	if (window.L && window.L.Control) addPdfTabs(window.L.Control.NotebookbarDraw);

	if (typeof MobileBottomBar !== 'undefined') {
		var getPdfItems = MobileBottomBar.prototype.getToolItems;
		MobileBottomBar.prototype.getToolItems = function () {
			var items = getPdfItems.call(this).slice();
			if (!isPdf()) return items;
			// Sul telefono: dopo Suite, gli strumenti del PDF più usati.
			var pdf = [
				{ type: 'customtoolitem', id: 'eurise-evidenzia', command: 'eurise-evidenzia', icon: 'lc_eurise_evidenzia.svg', text: t('Evidenzia', 'Highlight') },
				{ type: 'customtoolitem', id: 'eurise-penna', command: 'eurise-penna', icon: 'lc_eurise_penna.svg', text: t('Penna', 'Pen') },
				{ type: 'customtoolitem', id: 'eurise-condividi', command: 'eurise-condividi', icon: 'lc_eurise_condividi.svg', text: t('Condividi', 'Share') }
			];
			return items.slice(0, 1).concat(pdf, items.slice(1));
		};
	}

	function uno(command, args) { if (app.map) app.map.sendUnoCommand(command, args); }

	actions['eurise-evidenzia'] = function () {
		// Sabbia della suite (#E9CF8E) dietro al testo scelto.
		uno('.uno:CharBackColor', { CharBackColor: { type: 'long', value: 0xE9CF8E } });
	};
	actions['eurise-sottolinea'] = function () { uno('.uno:Underline'); };
	actions['eurise-penna'] = function () { uno('.uno:Freeline_Unfilled'); };
	actions['eurise-firma'] = function () {
		uno('.uno:Freeline_Unfilled');
		notice(t('Firma con il dito o la penna dove serve.', 'Sign with your finger or pen where needed.'));
	};
	actions['eurise-condividi'] = function () { beforeSharing(); };

	/** Qualità della copia: Schermo, Stampa, Sotto 1 MB (PEC). */
	var SIZES = [
		{ id: 'schermo', it: 'Schermo', en: 'Screen', dpi: 150, quality: 75 },
		{ id: 'stampa', it: 'Stampa', en: 'Print', dpi: 300, quality: 90 },
		{ id: 'pec', it: 'Sotto 1 MB (PEC)', en: 'Under 1 MB', dpi: 96, quality: 60 }
	];

	// Codice fiscale e IBAN (italiano e degli altri paesi, anche a gruppi di quattro).
	var REDACT = [
		'[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPR-T][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]',
		'[A-Z]{2}[0-9]{2}( ?[A-Z0-9]{4}){2,7}( ?[A-Z0-9]{1,4})?'
	];

	function newPassword() {
		var chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
		var out = '';
		var bytes = new Uint32Array(14);
		window.crypto.getRandomValues(bytes);
		for (var i = 0; i < bytes.length; i++) out += chars[bytes[i] % chars.length];
		return out.slice(0, 4) + '-' + out.slice(4, 9) + '-' + out.slice(9);
	}

	function beforeSharing() {
		var box = overlay('eurise-condividi');
		box.innerHTML = '';
		var title = document.createElement('div');
		title.className = 'eurise-title';
		title.textContent = t('Prima di condividere', 'Before sharing');
		box.appendChild(title);

		function check(label, sub, on) {
			var row = document.createElement('label');
			row.className = 'eurise-row eurise-check';
			var input = document.createElement('input');
			input.type = 'checkbox';
			input.checked = on;
			var text = document.createElement('span');
			text.textContent = label;
			if (sub) {
				var s = document.createElement('small');
				s.className = 'eurise-sub';
				s.textContent = sub;
				text.appendChild(document.createElement('br'));
				text.appendChild(s);
			}
			row.appendChild(input);
			row.appendChild(text);
			box.appendChild(row);
			return input;
		}

		var size = SIZES[0];
		var small = check(t('Riduci il peso', 'Reduce the size'), null, true);
		var sizes = document.createElement('div');
		sizes.className = 'eurise-pills';
		SIZES.forEach(function (sz) {
			var b = document.createElement('button');
			b.className = 'eurise-pill' + (sz === size ? ' on' : '');
			b.textContent = t(sz.it, sz.en);
			b.onclick = function () {
				size = sz;
				Array.prototype.forEach.call(sizes.children, function (c) { c.classList.remove('on'); });
				b.classList.add('on');
			};
			sizes.appendChild(b);
		});
		box.appendChild(sizes);
		var notes = check(t('Togli le note e i commenti', 'Remove notes and comments'), t('restano solo in questo file', 'they stay in this file only'), true);
		var redact = check(t('Oscura codici fiscali e IBAN', 'Redact tax codes and IBANs'),
			t('spariscono davvero dal testo; nelle pagine scansionate restano nell\'immagine', 'removed from the text; scanned pages keep them in the image'), false);
		var lock = check(t('Password per aprirlo', 'Password to open it'), null, false);
		var pwdRow = document.createElement('div');
		pwdRow.className = 'eurise-sub';
		pwdRow.style.display = 'none';
		box.appendChild(pwdRow);
		var password = newPassword();
		lock.onchange = function () {
			pwdRow.style.display = lock.checked ? '' : 'none';
			pwdRow.textContent = t('Password: ', 'Password: ') + password + t(' · copiata negli appunti: mandala a parte', ' · copied: send it separately');
			if (lock.checked && navigator.clipboard) navigator.clipboard.writeText(password).catch(function () {});
		};
		var note = document.createElement('div');
		note.className = 'eurise-sub';
		note.textContent = t('Si condivide una copia: questo file resta com\'è.', 'A copy is shared: this file stays as it is.');
		box.appendChild(note);

		function go(how) {
			var options = {
				ExportNotes: { type: 'boolean', value: notes.checked ? 'false' : 'true' },
				ExportNotesInMargin: { type: 'boolean', value: 'false' },
				ReduceImageResolution: { type: 'boolean', value: small.checked ? 'true' : 'false' }
			};
			if (small.checked) {
				options.MaxImageResolution = { type: 'long', value: String(size.dpi) };
				options.Quality = { type: 'long', value: String(size.quality) };
			}
			if (lock.checked) {
				options.EncryptFile = { type: 'boolean', value: 'true' };
				options.DocumentOpenPassword = { type: 'string', value: password };
			}
			var redacted = 0;
			if (redact.checked) {
				REDACT.forEach(function (expr) {
					uno('.uno:ExecuteSearch', {
						'SearchItem.SearchString': { type: 'string', value: expr },
						'SearchItem.ReplaceString': { type: 'string', value: '████████████████' },
						'SearchItem.Backward': { type: 'boolean', value: false },
						'SearchItem.Command': { type: 'long', value: 3 },
						'SearchItem.AlgorithmType': { type: 'short', value: 1 },
						'SearchItem.AlgorithmType2': { type: 'short', value: 2 },
						'SearchItem.SearchFlags': { type: 'long', value: 0 },
						'Quiet': { type: 'boolean', value: true }
					});
					redacted++;
				});
			}
			closeOverlay();
			var name = documentName().replace(/\.pdf$/i, '') + '.pdf';
			toShell({ action: 'pdf', how: how, name: name, options: JSON.stringify(options) }, function () {
				// La copia è fatta: l'oscuramento si toglie da questo file (il motore lavora i messaggi in ordine).
				for (var i = 0; i < redacted; i++) uno('.uno:Undo');
			});
		}

		var buttons = document.createElement('div');
		buttons.className = 'eurise-pills';
		var share = document.createElement('button');
		share.className = 'eurise-button';
		share.textContent = t('Condividi la copia', 'Share the copy');
		share.onclick = function () { go('condividi'); };
		var save = document.createElement('button');
		save.className = 'eurise-pill';
		save.textContent = t('Salva in…', 'Save in…');
		save.onclick = function () { go('salva'); };
		buttons.appendChild(share);
		buttons.appendChild(save);
		box.appendChild(buttons);
	}

	// --- Telefono: l'interfaccia Eurise al posto di quella di Collabora (tavola OffTelefono) -------------------
	//
	// In alto: indietro, nome e stato del salvataggio, ricerca, annulla, condividi (dentro #toolbar-row, così il
	// documento resta impaginato come Collabora lo calcola). In basso: la dock con le schede del tipo di documento; il
	// pannello di una scheda compare solo toccandola e si richiude toccandola di nuovo (come nell'editor PDF). Il
	// documento si apre già modificabile: niente matita. Le barre di Collabora restano nascoste (eurise.css).

	function dispatch(name) { if (app.dispatcher) app.dispatcher.dispatch(name); }

	var SVG = {
		back: 'M15 6l-6 6 6 6',
		search: 'M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14 M16 16l4 4',
		undo: 'M9 6L4 11l5 5 M4 11h10a6 6 0 0 1 0 12',
		share: 'M18 4a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5 M6 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5 M18 15a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5 M8.2 10.8l7.6-4.1 M8.2 13.2l7.6 4.1',
		star: 'M12 2 Q13.2 10.8 22 12 Q13.2 13.2 12 22 Q10.8 13.2 2 12 Q10.8 10.8 12 2Z'
	};

	function icon(path, size) {
		return '<svg width="' + (size || 20) + '" height="' + (size || 20) + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
			'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + path + '"></path></svg>';
	}

	/** Voce di un pannello: [etichetta it, en, comando .uno o funzione, segno da mostrare al posto dell'etichetta]. */
	function k(it, en, run, glyph) { return { label: t(it, en), run: run, glyph: glyph }; }

	var styleMenu = function () {
		chooser(t('Stile del paragrafo', 'Paragraph style'), [
			[t('Testo', 'Text'), 'Standard'], [t('Titolo 1', 'Heading 1'), 'Heading 1'],
			[t('Titolo 2', 'Heading 2'), 'Heading 2'], [t('Titolo 3', 'Heading 3'), 'Heading 3'],
			[t('Citazione', 'Quotation'), 'Quotations']
		], function (style) {
			uno('.uno:StyleApply', { Style: { type: 'string', value: style }, FamilyName: { type: 'string', value: 'ParagraphStyles' } });
		});
	};

	var clipboard = [
		k('Copia', 'Copy', '.uno:Copy'), k('Taglia', 'Cut', '.uno:Cut'), k('Incolla', 'Paste', '.uno:Paste'),
		k('Tutto', 'Select all', '.uno:SelectAll')
	];
	var suite = [
		k('Detta', 'Dictate', function () { actions['eurise-detta'](); }),
		k('Traduci', 'Translate', function () { actions['eurise-traduci'](); }),
		k('Da fare', 'To-do', function () { actions['eurise-dafare'](); }),
		k('Evento', 'Event', function () { actions['eurise-evento'](); }),
		k('Note', 'Notes', function () { actions['eurise-note'](); })
	];
	function more(extra) {
		return clipboard.concat(extra || []).concat([
			k('Trova', 'Find', function () { dispatch('showsearchbar'); }),
			k('PDF', 'PDF', function () { beforeSharing(); }),
			k('Stampa', 'Print', function () { dispatch('print'); }),
			k('Tutte le opzioni', 'All options', function () { dispatch('mobile_wizard'); })
		]);
	}

	var DOCKS = {
		text: [
			{ id: 'testo', label: t('Testo', 'Text'), keys: [
				k('Grassetto', 'Bold', '.uno:Bold', 'B'), k('Corsivo', 'Italic', '.uno:Italic', 'I'),
				k('Sottolineato', 'Underline', '.uno:Underline', 'U'), k('Stile', 'Style', styleMenu, t('Titolo ▾', 'Heading ▾')),
				k('Elenco', 'Bullets', '.uno:DefaultBullet', '•≡'), k('Numeri', 'Numbering', '.uno:DefaultNumbering', '1≡'),
				k('Centra', 'Center', '.uno:CenterPara', '≡'), k('Evidenzia', 'Highlight', function () { actions['eurise-evidenzia'](); })
			] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), keys: [
				k('Immagine', 'Image', function () { dispatch('localgraphic'); }),
				k('Tabella', 'Table', function () { uno('.uno:InsertTable', { Columns: { type: 'long', value: 2 }, Rows: { type: 'long', value: 3 } }); }),
				k('Collegamento', 'Link', function () { dispatch('hyperlinkdialog'); }),
				k('Commento', 'Comment', function () { dispatch('insertcomment'); }),
				k('Nuova pagina', 'Page break', '.uno:InsertPagebreak'), k('Data', 'Date', '.uno:InsertDateField'),
				k('Simbolo', 'Symbol', '.uno:CharmapControl')
			] },
			{ id: 'revisione', label: t('Revisione', 'Review'), keys: [
				k('Traccia modifiche', 'Track changes', '.uno:TrackChanges'), k('Accetta', 'Accept', '.uno:AcceptTrackedChange'),
				k('Rifiuta', 'Reject', '.uno:RejectTrackedChange'), k('Commento', 'Comment', function () { dispatch('insertcomment'); }),
				k('Ortografia', 'Spelling', '.uno:SpellingAndGrammarDialog'), k('Conta parole', 'Word count', '.uno:WordCountDialog')
			] },
			{ id: 'suite', label: 'Suite', suite: true, keys: suite },
			{ id: 'altro', label: t('Altro', 'More'), more: true, keys: more() }
		],
		spreadsheet: [
			{ id: 'celle', label: t('Celle', 'Cells'), keys: [
				k('Grassetto', 'Bold', '.uno:Bold', 'B'), k('Corsivo', 'Italic', '.uno:Italic', 'I'),
				k('Somma', 'Sum', '.uno:AutoSum', 'Σ'), k('Euro', 'Currency', '.uno:NumberFormatCurrency', '€'),
				k('Percento', 'Percent', '.uno:NumberFormatPercent', '%'), k('Unisci', 'Merge', '.uno:ToggleMergeCells'),
				k('Centra', 'Center', '.uno:AlignHorizontalCenter', '≡'), k('A capo', 'Wrap', '.uno:WrapText')
			] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), keys: [
				k('Riga sopra', 'Row above', '.uno:InsertRowsBefore'), k('Colonna prima', 'Column before', '.uno:InsertColumnsBefore'),
				k('Elimina riga', 'Delete row', '.uno:DeleteRows'), k('Elimina colonna', 'Delete column', '.uno:DeleteColumns'),
				k('Grafico', 'Chart', '.uno:InsertObjectChart'), k('Immagine', 'Image', function () { dispatch('localgraphic'); }),
				k('Commento', 'Comment', function () { dispatch('insertcomment'); }), k('Foglio', 'Sheet', '.uno:Insert')
			] },
			{ id: 'dati', label: t('Dati', 'Data'), keys: [
				k('Ordina A-Z', 'Sort A-Z', '.uno:SortAscending'), k('Ordina Z-A', 'Sort Z-A', '.uno:SortDescending'),
				k('Filtro', 'Filter', '.uno:DataFilterAutoFilter'), k('Blocca', 'Freeze', '.uno:FreezePanes'),
				k('Ricalcola', 'Recalculate', '.uno:Calculate')
			] },
			{ id: 'suite', label: 'Suite', suite: true, keys: suite.slice(0, 4).concat([
				k('Viaggio', 'Trip', function () { actions['eurise-viaggio'](); }),
				k('Grafico', 'Chart', function () { actions['eurise-grafico'](); })
			]) },
			{ id: 'altro', label: t('Altro', 'More'), more: true, keys: more() }
		],
		presentation: [
			{ id: 'diapositiva', label: t('Diapositiva', 'Slide'), keys: [
				k('Nuova', 'New', '.uno:InsertPage'), k('Duplica', 'Duplicate', '.uno:DuplicatePage'),
				k('Elimina', 'Delete', '.uno:DeletePage'), k('Presenta', 'Present', function () { dispatch('presentation'); }),
				k('Dalla corrente', 'From current', function () { dispatch('presentation-currentslide'); })
			] },
			{ id: 'testo', label: t('Testo', 'Text'), keys: [
				k('Grassetto', 'Bold', '.uno:Bold', 'B'), k('Corsivo', 'Italic', '.uno:Italic', 'I'),
				k('Sottolineato', 'Underline', '.uno:Underline', 'U'), k('Più grande', 'Bigger', '.uno:Grow', 'A+'),
				k('Più piccolo', 'Smaller', '.uno:Shrink', 'A−'), k('Elenco', 'Bullets', '.uno:DefaultBullet', '•≡'),
				k('Centra', 'Center', '.uno:CenterPara', '≡')
			] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), keys: [
				k('Casella di testo', 'Text box', '.uno:Text'), k('Immagine', 'Image', function () { dispatch('localgraphic'); }),
				k('Tabella', 'Table', function () { uno('.uno:InsertTable', { Columns: { type: 'long', value: 2 }, Rows: { type: 'long', value: 2 } }); }),
				k('Forma', 'Shape', '.uno:BasicShapes.rectangle'), k('Commento', 'Comment', function () { dispatch('insertcomment'); })
			] },
			{ id: 'suite', label: 'Suite', suite: true, keys: suite },
			{ id: 'altro', label: t('Altro', 'More'), more: true, keys: more() }
		]
	};
	DOCKS.drawing = DOCKS.presentation;

	var phone = { open: null, dock: null, panel: null, saved: true, saveTimer: null };

	function phoneHeader() {
		var row = document.getElementById('toolbar-row');
		if (!row || document.getElementById('eurise-header')) return;
		var h = document.createElement('div');
		h.id = 'eurise-header';
		h.innerHTML =
			'<button type="button" class="eurise-hbtn" data-a="back" aria-label="' + t('Indietro', 'Back') + '">' + icon(SVG.back) + '</button>' +
			'<span class="eurise-doc"><span class="eurise-name"></span><span class="eurise-status"></span></span>' +
			'<button type="button" class="eurise-hbtn" data-a="search" aria-label="' + t('Ricerca rapida', 'Quick search') + '">' + icon(SVG.search, 19) + '</button>' +
			'<button type="button" class="eurise-hbtn" data-a="undo" aria-label="' + t('Annulla', 'Undo') + '">' + icon(SVG.undo, 19) + '</button>' +
			'<button type="button" class="eurise-hbtn" data-a="share" aria-label="' + t('Condividi', 'Share') + '">' + icon(SVG.share, 19) + '</button>';
		h.querySelector('.eurise-name').textContent = documentName();
		h.onclick = function (e) {
			var b = e.target.closest && e.target.closest('button');
			if (!b) return;
			var a = b.getAttribute('data-a');
			if (a === 'back') {
				if (window.ThisIsAMobileApp) window.postMobileMessage('BYE'); else dispatch('closemobile');
			} else if (a === 'search') openSearch();
			else if (a === 'undo') uno('.uno:Undo');
			else if (a === 'share') beforeSharing();
		};
		row.appendChild(h);
		status();
	}

	function status() {
		var el = document.querySelector('#eurise-header .eurise-status');
		if (el) el.textContent = phone.saved ? t('Salvato', 'Saved') : t('Modifiche in corso…', 'Editing…');
		if (el) el.classList.toggle('pending', !phone.saved);
	}

	/** Salva da sé poco dopo l'ultima modifica: il motore scrive nel file originale (LOActivity, SAVE). */
	function scheduleSave() {
		clearTimeout(phone.saveTimer);
		phone.saveTimer = setTimeout(function () {
			if (!phone.saved) uno('.uno:Save', { DontTerminateEdit: { type: 'boolean', value: true } });
		}, 4000);
	}

	function phoneDock(docType) {
		if (document.getElementById('eurise-dock')) return;
		var tabs = DOCKS[docType] || DOCKS.text;
		var dock = document.createElement('nav');
		dock.id = 'eurise-dock';
		dock.setAttribute('aria-label', t('Strumenti', 'Tools'));
		tabs.forEach(function (tab) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-tab' + (tab.suite ? ' suite' : '') + (tab.more ? ' more' : '');
			b.innerHTML = (tab.suite ? '<svg width="11" height="11" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="' + SVG.star + '"></path></svg>' : '') +
				'<span>' + tab.label + '</span>';
			b.onclick = function () { togglePanel(tab, b); };
			dock.appendChild(b);
		});
		document.body.appendChild(dock);
		phone.dock = dock;
	}

	function togglePanel(tab, button) {
		var same = phone.open === tab.id;
		closePanel();
		if (same) return;
		phone.open = tab.id;
		button.classList.add('active');
		var panel = document.createElement('div');
		panel.id = 'eurise-panel';
		panel.className = tab.suite ? 'suite' : '';
		panel.setAttribute('role', 'toolbar');
		panel.setAttribute('aria-label', tab.label);
		tab.keys.forEach(function (key) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-key' + (key.glyph ? ' glyph' : '');
			b.textContent = key.glyph || key.label;
			b.title = key.label;
			b.setAttribute('aria-label', key.label);
			if (typeof key.run === 'string') {
				b.setAttribute('data-uno', key.run);
				if (phone.states[key.run] === 'true') b.classList.add('on');
			}
			b.onclick = function () {
				if (typeof key.run === 'string') uno(key.run); else key.run();
			};
			panel.appendChild(b);
		});
		document.body.appendChild(panel);
		phone.panel = panel;
	}

	function closePanel() {
		phone.open = null;
		if (phone.panel) phone.panel.remove();
		phone.panel = null;
		if (phone.dock) Array.prototype.forEach.call(phone.dock.querySelectorAll('.active'), function (b) { b.classList.remove('active'); });
	}

	phone.states = {};

	function onState(e) {
		var name = e.commandName;
		var state = e.state;
		if (name === '.uno:ModifiedStatus') {
			phone.saved = state !== 'true';
			status();
			if (!phone.saved) scheduleSave();
			return;
		}
		if (typeof state !== 'string') return;
		phone.states[name] = state;
		if (phone.panel) {
			Array.prototype.forEach.call(phone.panel.querySelectorAll('[data-uno="' + name + '"]'), function (b) {
				b.classList.toggle('on', state === 'true');
			});
		}
	}

	/** Chi può modificare entra subito in modifica, senza la matita di Collabora. */
	function editAtOnce(map) {
		var setPermission = map.setPermission;
		map.setPermission = function (perm) {
			setPermission.apply(map, arguments);
			if (perm === 'edit' && map.isReadOnlyMode && map.isReadOnlyMode() && map._switchToEditMode) {
				setTimeout(function () { map._switchToEditMode(); }, 0);
			}
		};
	}

	function chooser(title, items, done) {
		var box = overlay('eurise-chooser');
		var h = document.createElement('h2');
		h.textContent = title;
		box.appendChild(h);
		items.forEach(function (it) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-row';
			b.textContent = it[0];
			b.onclick = function () { closeOverlay(); done(it[1]); };
			box.appendChild(b);
		});
	}

	function setupPhone() {
		var map = app.map;
		if (!map || !window.mode || !window.mode.isMobile()) return;
		document.body.classList.add('eurise-tel');
		editAtOnce(map);
		function build() {
			// Un PDF aperto nel motore ha già le sue schede Annota e Pagine: lì resta l'interfaccia di Collabora.
			if (isPdf()) { document.body.classList.remove('eurise-tel'); return; }
			phoneHeader();
			phoneDock(map.getDocType());
		}
		if (map._docLayer) build(); else map.on('doclayerinit', build);
		map.on('commandstatechanged', onState);
		// Il pannello si chiude da solo quando si apre un dialogo o il menu di Collabora.
		map.on('mobilewizard closealldialogs', closePanel);
	}

	(function waitForMap(tries) {
		if (window.app && app.map && app.map.on) { setupPhone(); return; }
		if (tries > 0) setTimeout(function () { waitForMap(tries - 1); }, 100);
	})(300);

	window.euriseOffice = { reply: reply, openSearch: openSearch, notice: notice };
	if (typeof JSDialog !== 'undefined') JSDialog.EuriseSearch = openSearch;
})();

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

	// --- Icone Eurise al posto di quelle di Collabora (richiesta del proprietario, 14 novembre) --------------------
	//
	// Un solo punto: LOUtil.getImageURL, che serve il nastro, la colonna Properties, i menu e i dialoghi. Se l'icona
	// chiesta (lc_bold.svg, sc_bold.svg…) è tra le nostre, si dà la nostra come immagine SVG in linea, nei colori della
	// suite: tratto Notte, dettagli in azzurro, tinte della suite dove l'icona parla di colore; versione chiara per il tema
	// scuro. Le altre restano quelle di Collabora. Livelli: m = tratto principale, a = accento, f = campitura leggera
	// dell'accento, #rrggbb = tratto di quel colore, *#rrggbb = campitura piena di quel colore.
	var ICONS = {
		// Appunti
		paste: [['m', 'M9 4h6v3H9z M7.5 5.5H6v15h12v-15h-1.5'], ['a', 'M9.5 12h5 M9.5 15.5h3.5']],
		cut: [['m', 'M6.5 4.5l11 11 M17.5 4.5l-11 11'], ['a', 'M6 16.5a2.5 2.5 0 1 0 0 .01 M18 16.5a2.5 2.5 0 1 0 0 .01']],
		copy: [['f', 'M9 8h11v12H9z'], ['m', 'M9 8h11v12H9z M15 8V4H4v12h5']],
		formatpaintbrush: [['m', 'M5 4h12v5H5z M17 6.5h2v4.5h-8v3'], ['a', 'M10 14h2v6h-2z']],
		// Carattere
		bold: [['m', 'M7.5 4.5h5.2a3.6 3.6 0 0 1 0 7.2H7.5z M7.5 11.7h6.2a3.9 3.9 0 0 1 0 7.8H7.5z']],
		italic: [['m', 'M10 4.5h8 M6 19.5h8 M14.5 4.5l-5 15']],
		underline: [['m', 'M7 4.5v6.5a5 5 0 0 0 10 0V4.5'], ['a', 'M5.5 20h13']],
		strikeout: [['m', 'M16.5 7.2C15.8 5.6 14.2 4.7 12 4.7c-2.6 0-4.3 1.3-4.3 3.2 0 1.3.8 2.1 2 2.6 M8 16.4c.6 1.8 2.2 2.9 4.2 2.9 2.7 0 4.4-1.4 4.4-3.4 0-.8-.3-1.5-.8-2'], ['a', 'M4.5 12h15']],
		superscript: [['m', 'M4.5 9l8 10.5 M12.5 9l-8 10.5'], ['a', 'M15.5 5.2a1.9 1.9 0 0 1 3.6.9c0 1.4-3.6 2.4-3.6 4h3.9']],
		subscript: [['m', 'M4.5 4.5L12.5 15 M12.5 4.5L4.5 15'], ['a', 'M15.5 14.2a1.9 1.9 0 0 1 3.6.9c0 1.4-3.6 2.4-3.6 4h3.9']],
		color: [['m', 'M6.5 15.5L12 3.5l5.5 12 M8.6 11h6.8'], ['*#B4441A', 'M4 18h16v3H4z']],
		fontcolor: 'color',
		charbackcolor: [['m', 'M13.5 4.5l5 5-7 7H6.5v-5z M9 9l5 5'], ['*#F2D98B', 'M4 18.5h16v2.5H4z']],
		backcolor: 'charbackcolor',
		backgroundcolor: [['m', 'M5 11l6-6.5 7 7-6 6.5z M5 11h13'], ['f', 'M5 11h13l-6 6.5z'], ['a', 'M19.5 15.5c0 1.5-1 2.5-1 3.5a1 1 0 0 0 2 0c0-1-1-2-1-3.5z']],
		grow: [['m', 'M3.5 19L8.5 7l5 12 M5.4 15h6.2'], ['a', 'M18 13V4 M15 7l3-3 3 3']],
		shrink: [['m', 'M3.5 19L8.5 7l5 12 M5.4 15h6.2'], ['a', 'M18 4v9 M15 10l3 3 3-3']],
		resetattributes: [['m', 'M5 19L10.5 5h3L8 19 M7.5 12.5h5'], ['a', 'M15 14l5.5 5.5 M20.5 14L15 19.5']],
		setdefault: 'resetattributes',
		shadowed: [['f', 'M8 20l6-15.5 6 15.5'], ['m', 'M5 18L11 3.5 17 18 M7.4 12.5h7.2']],
		spacing: [['m', 'M5 13l3.5-8.5 3.5 8.5 M6 10.5h5 M13 13l3-8.5 3 8.5'], ['a', 'M4 18.5h16 M6.5 16.5l-2.5 2 2.5 2 M17.5 16.5l2.5 2-2.5 2']],
		// Paragrafo
		defaultbullet: [['a', 'M5 6.5h.01 M5 12h.01 M5 17.5h.01'], ['m', 'M9.5 6.5h10 M9.5 12h10 M9.5 17.5h10']],
		defaultnumbering: [['a', 'M4.5 5l1.5-1v4.5 M3.8 14.5a1.4 1.4 0 0 1 2.7.6c0 .9-2.7 1.8-2.7 2.9h2.9'], ['m', 'M10 6.5h10 M10 12h10 M10 17.5h10']],
		leftpara: [['m', 'M4 5.5h16 M4 10h10 M4 14.5h16'], ['a', 'M4 19h10']],
		centerpara: [['m', 'M4 5.5h16 M7 10h10 M4 14.5h16'], ['a', 'M7 19h10']],
		rightpara: [['m', 'M4 5.5h16 M10 10h10 M4 14.5h16'], ['a', 'M10 19h10']],
		justifypara: [['m', 'M4 5.5h16 M4 10h16 M4 14.5h16'], ['a', 'M4 19h16']],
		alignleft: 'leftpara',
		alignhorizontalcenter: 'centerpara',
		alignright: 'rightpara',
		alignblock: 'justifypara',
		incrementindent: [['m', 'M4 5h16 M11 9.7h9 M11 14.3h9 M4 19h16'], ['a', 'M4 9l3.2 3L4 15']],
		decrementindent: [['m', 'M4 5h16 M11 9.7h9 M11 14.3h9 M4 19h16'], ['a', 'M7.2 9L4 12l3.2 3']],
		hangingindent: [['m', 'M4 5h16 M9 9.7h11 M9 14.3h11 M4 19h16'], ['a', 'M4 9.7h2 M4 14.3h2']],
		paralefttoright: [['m', 'M10 4.5v10 M14 4.5v10 M9 4.5h7 M10 4.5a3 3 0 0 0 0 6'], ['a', 'M5 19h14 M16.5 16.5L19 19l-2.5 2.5']],
		pararighttoleft: [['m', 'M10 4.5v10 M14 4.5v10 M9 4.5h7 M10 4.5a3 3 0 0 0 0 6'], ['a', 'M5 19h14 M7.5 16.5L5 19l2.5 2.5']],
		linespacing: [['m', 'M11 6h9 M11 12h9 M11 18h9'], ['a', 'M5.5 4v16 M3.5 6l2-2 2 2 M3.5 18l2 2 2-2']],
		paraspaceincrease: [['m', 'M11 8.5h9 M11 15.5h9'], ['a', 'M5.5 4v16 M3.5 6l2-2 2 2 M3.5 18l2 2 2-2']],
		paraspacedecrease: [['m', 'M11 10h9 M11 14h9'], ['a', 'M5.5 3v6 M3.5 7l2 2 2-2 M5.5 21v-6 M3.5 17l2-2 2 2']],
		// Stili
		stylenewbyexample: [['m', 'M5 18.5L10 5.5l5 13 M6.9 14h6.2'], ['a', 'M18.5 4v6 M15.5 7h6']],
		styleupdatebyexample: [['m', 'M5 18.5L10 5.5l5 13 M6.9 14h6.2'], ['a', 'M21 7.5a3 3 0 1 1-1-2.2 M20.5 3.2v2.4h-2.4']],
		stylewatercanmode: [['m', 'M5 9h10v8a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z M15 11l5-4'], ['a', 'M20.5 10.5v.01 M19 13v.01']],
		editstyle: [['m', 'M5 18.5L10 5.5l5 13 M6.9 14h6.2'], ['a', 'M16 19.5l4.5-4.5-1.5-1.5-4.5 4.5v1.5z']],
		// Modifica e inserimento
		searchdialog: [['m', 'M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13 M15.3 15.3L20 20'], ['a', 'M8 10.5h5']],
		hyperlinkdialog: [['m', 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1'], ['a', 'M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1']],
		inserttable: [['f', 'M4 5h16v4.5H4z'], ['m', 'M4 5h16v14H4z M4 9.5h16 M4 14.2h16 M10 9.5V19 M15 9.5V19']],
		insertgraphic: [['m', 'M4 5h16v14H4z'], ['f', 'M4 19l6-7 4 4.5 2.5-2.5 3.5 5z'], ['a', 'M4 17l6-6 4 4.5 2.5-2.5L20 16.5 M15.5 8.5h.01']],
		insertobjectchart: [['m', 'M4 20h16'], ['f', 'M6 14h3v6H6z M10.5 8h3v12h-3z M15 11h3v9h-3z'], ['a', 'M6 14h3v6 M10.5 20V8h3v12 M15 20v-9h3v9']],
		'basicshapes.rectangle': [['f', 'M4.5 6.5h15v11h-15z'], ['m', 'M4.5 6.5h15v11h-15z']],
		basicshapes: 'basicshapes.rectangle',
		insertannotation: [['m', 'M4.5 5h15v10.5h-8L7 19.5v-4H4.5z'], ['a', 'M8 9h8 M8 12h5']],
		insertfootnote: [['m', 'M4 5.5h16 M4 10h16 M4 14.5h9'], ['a', 'M16.5 15.2l1.6-1v5.3 M4 19.5h4']],
		charmapcontrol: [['m', 'M6.5 19.5h4v-2.2A6.5 6.5 0 1 1 13.5 17.3v2.2h4']],
		insertdatefield: [['m', 'M4 6h16v14H4z M8 3.5v4 M16 3.5v4 M4 10h16'], ['a', 'M8 14h.01 M12 14h.01 M16 14h.01 M8 17h.01 M12 17h.01']],
		insertpagenumberfield: [['m', 'M6 3.5h9l3.5 3.5v13.5H6z M15 3.5v3.5h3.5'], ['a', 'M10.5 12.2l1.7-1.2v6']],
		insertpagebreak: [['m', 'M6 3.5v5h12v-5 M6 20.5v-5h12v5'], ['a', 'M3.5 12h2 M8 12h2 M12.5 12h2 M17 12h2']],
		insertbreak: 'insertpagebreak',
		insertmultiindex: [['m', 'M9.5 6h10 M11.5 12h8 M11.5 18h8'], ['a', 'M4.5 6h.01 M6.5 12h.01 M6.5 18h.01']],
		pagedialog: [['m', 'M6 3.5h12v17H6z'], ['a', 'M9 7h6 M9 17h6 M9 7v10 M15 7v10']],
		formatcolumns: [['m', 'M4 4.5h16v15H4z'], ['a', 'M12 4.5v15'], ['m', 'M6.5 8h3 M6.5 11h3 M14.5 8h3 M14.5 11h3']],
		spellingandgrammardialog: [['m', 'M3.5 14L7 5l3.5 9 M4.7 11h4.6 M12.5 5v9h2.6a2.3 2.3 0 0 0 0-4.6h-2.6'], ['a', 'M8 18l2.5 2.5L16 15']],
		spelldialog: 'spellingandgrammardialog',
		spellingdialog: 'spellingandgrammardialog',
		wordcountdialog: [['m', 'M5 4h14v16H5z M8.5 8h7 M8.5 11.5h7'], ['a', 'M8.5 15.5h1.2 M11.4 15.5h1.2 M14.3 15.5h1.2']],
		trackchanges: [['m', 'M4 18h4l10-10-4-4L4 14z'], ['a', 'M12 20h8']],
		accepttrackedchange: [['m', 'M4 6h11 M4 10.5h8'], ['a', 'M10 17l3 3 7-8']],
		rejecttrackedchange: [['m', 'M4 6h11 M4 10.5h8'], ['a', 'M12 13.5l7 7 M19 13.5l-7 7']],
		accepttrackedchanges: [['m', 'M4 5h16 M4 9.5h10'], ['a', 'M4 15.5l2.5 2.5L11 13.5 M13 15.5l2.5 2.5L20 13.5']],
		zoomplus: [['m', 'M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13 M15.3 15.3L20 20'], ['a', 'M10.5 7.5v6 M7.5 10.5h6']],
		zoomminus: [['m', 'M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13 M15.3 15.3L20 20'], ['a', 'M7.5 10.5h6']],
		fullscreen: [['m', 'M4 9V4h5 M20 9V4h-5 M4 15v5h5 M20 15v5h-5'], ['f', 'M8 8h8v8H8z']],
		ruler: [['m', 'M3.5 8h17v8h-17z'], ['a', 'M7 8v3 M10.5 8v4 M14 8v3 M17.5 8v4']],
		controlcodes: [['m', 'M13 4.5v15 M17 4.5v15 M11 4.5h8.5'], ['a', 'M13 4.5H10a4 4 0 0 0 0 8h3']],
		formattingmarkmenu: 'controlcodes',
		navigator: [['m', 'M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17'], ['f', 'M15.5 8.5l-2 5-5 2 2-5z'], ['a', 'M15.5 8.5l-2 5-5 2 2-5z']],
		// Celle
		wraptext: [['m', 'M4 6h16 M4 12h12.5a3 3 0 0 1 0 6H12'], ['a', 'M14 16l-2 2 2 2'], ['m', 'M4 18h5']],
		togglemergecells: [['m', 'M4 5h16v14H4z'], ['a', 'M7.5 12h9 M9.5 10l-2 2 2 2 M14.5 10l2 2-2 2']],
		mergecells: 'togglemergecells',
		setborderstyle: [['m', 'M4 4h16v16H4z'], ['a', 'M12 4v16 M4 12h16']],
		aligntop: [['a', 'M4 4.5h16'], ['m', 'M8 8.5h3v11H8z M13 8.5h3v6h-3z']],
		alignverticalcenter: [['a', 'M4 12h16'], ['m', 'M8 6.5h3v11H8z M13 9h3v6h-3z']],
		alignbottom: [['a', 'M4 19.5h16'], ['m', 'M8 4.5h3v11H8z M13 9.5h3v6h-3z']],
		numberformatcurrency: [['m', 'M17 6.5a6 6 0 1 0 0 11'], ['a', 'M5 10.5h8 M5 13.5h8']],
		numberformatpercent: [['m', 'M18.5 5.5l-13 13'], ['a', 'M7 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4 M17 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4']],
		numberformatthousands: [['m', 'M4.5 9.5l1.5-1v7 M9 9.5a1.6 1.6 0 0 1 3 .8c0 1.4-3 2.6-3 5.2h3.2 M15 9h3.5l-2 2.5a2 2 0 1 1-1.8 2.7'], ['a', 'M13.2 16.5l-.6 1.6']],
		numberformatdate: 'insertdatefield',
		numberformatdecimal: [['m', 'M8 8.5a2.5 3.5 0 1 0 0 7a2.5 3.5 0 1 0 0-7 M16 8.5a2.5 3.5 0 1 0 0 7a2.5 3.5 0 1 0 0-7'], ['a', 'M12 15.5h.01']],
		numberformatincdecimals: [['m', 'M5 15.5h.01 M9.5 8.5a2 3.5 0 1 0 0 7a2 3.5 0 1 0 0-7'], ['a', 'M16 12h5 M18.5 9.5v5']],
		numberformatdecdecimals: [['m', 'M5 15.5h.01 M9.5 8.5a2 3.5 0 1 0 0 7a2 3.5 0 1 0 0-7'], ['a', 'M16 12h5']],
		autosum: [['m', 'M17.5 6V4.5h-11l6 7.5-6 7.5h11V18']],
		sortascending: [['m', 'M4 10l2.5-6 2.5 6 M4.8 8h3.4 M4 14h5l-5 6h5'], ['a', 'M17 4v16 M14 17l3 3 3-3']],
		sortdescending: [['m', 'M4 4h5L4 10h5 M4 20l2.5-6 2.5 6 M4.8 18h3.4'], ['a', 'M17 4v16 M14 17l3 3 3-3']],
		datasort: 'sortascending',
		datafilterautofilter: [['f', 'M4 5h16l-6 7.5V19l-4-2v-4.5z'], ['m', 'M4 5h16l-6 7.5V19l-4-2v-4.5z']],
		datafilterstandardfilter: [['m', 'M4 5h16l-6 7.5V19l-4-2v-4.5z'], ['a', 'M17 15h4 M17 18h4']],
		insertrowsbefore: [['m', 'M4 12h16v8H4z M4 16h16'], ['a', 'M12 3v6 M9 6h6']],
		insertrowsafter: [['m', 'M4 4h16v8H4z M4 8h16'], ['a', 'M12 15v6 M9 18h6']],
		insertcolumnsbefore: [['m', 'M12 4h8v16h-8z M16 4v16'], ['a', 'M3 12h6 M6 9v6']],
		insertcolumnsafter: [['m', 'M4 4h8v16H4z M8 4v16'], ['a', 'M15 12h6 M18 9v6']],
		deleterows: [['m', 'M4 8h16v8H4z'], ['a', 'M9 10l6 4 M15 10l-6 4']],
		deletecolumns: [['m', 'M8 4h8v16H8z'], ['a', 'M10 9l4 6 M14 9l-4 6']],
		functiondialog: [['m', 'M11 20c2 0 2.2-2 2.6-5l1-7c.4-2.6.8-4 3-4 M9.5 10h7'], ['a', 'M4 13l4 5 M8 13l-4 5']],
		calculate: [['m', 'M6 3.5h12v17H6z M8.5 6.5h7v3h-7z'], ['a', 'M9 13h.01 M12 13h.01 M15 13h.01 M9 16.5h.01 M12 16.5h.01 M15 16.5h.01']],
		definename: [['m', 'M4 12.5V5h7.5l8.5 8.5-7.5 7.5z'], ['a', 'M8 8.5h.01']],
		validation: [['m', 'M4 5h16v14H4z M4 9.5h16'], ['a', 'M8.5 14l2 2 4-4.5']],
		datasubtotals: [['m', 'M4 5h16 M4 9h16 M4 13h16'], ['a', 'M4 18h16 M15 15.5v5']],
		datadatapilotrun: [['m', 'M4 4h16v16H4z M4 9h16 M9 4v16'], ['a', 'M12.5 13h4.5v4.5 M17 13l-4.5 4.5']],
		protect: [['m', 'M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3'], ['a', 'M12 14.5v2']],
		freezepanes: [['m', 'M4 4h16v16H4z'], ['a', 'M4 9h16 M9 4v16']],
		togglesheetgrid: [['m', 'M4 4h16v16H4z M4 9.3h16 M4 14.7h16 M9.3 4v16 M14.7 4v16']],
		viewrowcolumnheaders: [['f', 'M4 4h16v4H4z M4 8h4v12H4z'], ['m', 'M4 4h16v16H4z M4 8h16 M8 4v16']],
		// Diapositive
		insertpage: [['m', 'M3.5 6h13v10h-13z'], ['a', 'M19.5 14v6 M16.5 17h6']],
		insertslide: 'insertpage',
		duplicatepage: [['f', 'M7.5 9h13v10h-13z'], ['m', 'M7.5 9h13v10h-13z M3.5 15V5h13']],
		duplicateslide: 'duplicatepage',
		deletepage: [['m', 'M3.5 6h13v10h-13z'], ['a', 'M17 16l4 4 M21 16l-4 4']],
		deleteslide: 'deletepage',
		text: [['m', 'M5 6V4.5h14V6 M12 4.5v15 M9.5 19.5h5']],
		normalmultipanegui: [['m', 'M3.5 5h17v14h-17z M8 5v14'], ['a', 'M10.5 8h7.5v6h-7.5z']],
		notesmode: [['m', 'M5 3.5h14v17H5z M8 6.5h8v6H8z'], ['a', 'M8 15.5h8 M8 18h5']],
		diamode: [['m', 'M3.5 5h7v5.5h-7z M13.5 5h7v5.5h-7z M3.5 13.5h7V19h-7z M13.5 13.5h7V19h-7z']],
		presentation: [['m', 'M3.5 4.5h17v11h-17z M12 15.5V20 M8.5 20h7'], ['a', 'M10.5 7.5v5l4-2.5z']],
		// File
		exporttopdf: [['m', 'M6 3.5h8.5L18 7v13.5H6z M14.5 3.5V7H18'], ['a', 'M12 10.5v6.5 M9.3 14.3L12 17l2.7-2.7']],
		print: [['m', 'M7 9V3.5h10V9 M7 17H4.5V9h15v8H17 M7 13.5h10v7H7z'], ['a', 'M16.5 11.5h.01']],
		save: [['m', 'M5 4h11.5L19.5 7v13H5z M8 4v5h7V4'], ['a', 'M8 20v-6h8v6']],
		undo: [['m', 'M9 5L4 10l5 5 M4 10h10.5a5 5 0 0 1 0 10H11']],
		redo: [['m', 'M15 5l5 5-5 5 M20 10H9.5a5 5 0 0 0 0 10H13']],
		morebutton: [['m', 'M8 16L16 8 M10 8h6v6']],
		// Nastro completo del computer (tavola OffPCNastro, 15 novembre)
		titlepagedialog: [['m', 'M6 3h12v18H6z'], ['a', 'M9 8h6 M9 11h4']],
		insertsection: [['m', 'M4 5h16 M4 19h16'], ['a', 'M4 9.5h16v5H4z']],
		fontworkgalleryfloater: [['m', 'M4 18c4-10 12-10 16 0'], ['a', 'M8 18l4-10 4 10 M9.5 14.5h5']],
		insertqrcode: [['m', 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z'], ['a', 'M14 14h2v2h-2z M18 18h2v2h-2z M14 18h2 M18 14h2']],
		insertframe: [['m', 'M4 4h16v16H4z'], ['a', 'M8 8h8v8H8z']],
		insertbookmark: [['m', 'M7 3h10v18l-5-4-5 4z'], ['a', 'M10 8h4']],
		insertreferencefield: [['m', 'M4 4h16v16H4z M8 12h8 M8 16h4'], ['a', 'M8 8h8']],
		insertpageheader: [['m', 'M5 3h14v18H5z'], ['a', 'M5 8h14 M8 5.5h5']],
		insertpagefooter: [['m', 'M5 3h14v18H5z'], ['a', 'M5 16h14 M8 18.5h5']],
		pagenumberwizard: [['m', 'M6 3h12v18H6z'], ['a', 'M11 16l1.5-1.2V19']],
		drawtext: [['m', 'M4 5h16v14H4z'], ['a', 'M8 9h8 M12 9v7']],
		insertfieldctrl: [['m', 'M3 7h18v10H3z'], ['a', 'M7 12h.01 M12 12h.01 M17 12h.01']],
		insertsignatureline: [['m', 'M3 20h18'], ['a', 'M3 16c3 0 4-8 6-8s0 8 2 8 3-5 5-5 2 3 5 3']],
		insertobjectstarmath: [['m', 'M5 5h9 M5 5l5 7-5 7h9'], ['a', 'M15 12h5 M17.5 9.5v5']],
		themedialog: [['m', 'M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-2 0-3 4 0 5-2c1.5-3-2.5-11-7-11z'], ['a', 'M7.5 11h.01 M10 7h.01 M15 8h.01']],
		watermark: [['m', 'M6 3h12v18H6z'], ['a', 'M9 15l6-6']],
		eurisepagecolor: [['f', 'M6 3h12v18H6z'], ['m', 'M6 3h12v18H6z'], ['a', 'M9 6h6v12H9z']],
		eurisepagemargin: [['m', 'M5 3h14v18H5z'], ['a', 'M8.5 3v18 M15.5 3v18']],
		orientation: [['m', 'M7 3h10v18H7z'], ['a', 'M3 15h4 M17 9h4']],
		eurisepagesize: [['m', 'M6 3h12v18H6z'], ['a', 'M9 7h6 M9 7l1.5-1.5 M9 7l1.5 1.5']],
		linenumberingdialog: [['m', 'M9 6h11 M9 12h11 M9 18h11'], ['a', 'M4 6h1 M4 12h1 M4 18h1']],
		hyphenate: [['m', 'M14 7h6 M14 12h6 M14 17h6'], ['a', 'M4 12h6']],
		paragraphdialog: [['m', 'M13 4v16 M17 4v16 M19 4h-9a4 4 0 0 0 0 8h3']],
		eurisewrap: [['m', 'M4 6h16 M4 18h16 M4 10h5 M4 14h5'], ['a', 'M11 10h6v4h-6z']],
		objectforwardone: [['m', 'M4 4h10v10H4z'], ['a', 'M10 10h10v10H10z']],
		objectbackone: [['a', 'M4 4h10v10H4z'], ['m', 'M10 10h10v10H10z']],
		bringtofront: [['m', 'M4 4h10v10H4z'], ['f', 'M10 10h10v10H10z'], ['a', 'M10 10h10v10H10z']],
		sendtoback: [['f', 'M4 4h10v10H4z'], ['a', 'M4 4h10v10H4z'], ['m', 'M10 10h10v10H10z']],
		formatgroup: [['m', 'M4 4h7v7H4z M13 13h7v7h-7z'], ['a', 'M4 13h7v7H4z M13 4h7v7h-7z']],
		formatungroup: [['m', 'M4 4h7v7H4z M13 13h7v7h-7z'], ['a', 'M15 6l4 4']],
		transformdialog: [['m', 'M4 4h16v16H4z'], ['a', 'M9 9h6v6H9z']],
		updatecurindex: [['m', 'M20 12a8 8 0 1 1-2.3-5.7'], ['a', 'M20 4v4h-4']],
		updateall: [['m', 'M20 12a8 8 0 1 1-2.3-5.7 M9 12h6'], ['a', 'M20 4v4h-4']],
		insertendnote: [['m', 'M4 5h16 M4 9h16 M4 13h10'], ['a', 'M4 19h5 M15 17l1-1v4']],
		footnotedialog: [['m', 'M4 5h16 M4 9h16'], ['a', 'M4 15h5 M14 13l1-1v4']],
		insertcaptiondialog: [['m', 'M4 4h16v11H4z'], ['a', 'M7 19h10']],
		insertindexesentry: [['m', 'M5 4h11l3 3v13H5z'], ['a', 'M8 10h8 M8 14h5']],
		thesaurusdialog: [['m', 'M3 6c3-1 6-1 9 1 3-2 6-2 9-1v13c-3-1-6-1-9 1-3-2-6-2-9-1z'], ['a', 'M12 7v13']],
		accessibilitycheck: [['m', 'M5 9l7 1 7-1 M12 10v4l-3 6 M12 14l3 6'], ['a', 'M12 4a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3']],
		languagemenu: [['m', 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M3 12h18'], ['a', 'M12 3c3 3 3 15 0 18 M12 3c-3 3-3 15 0 18']],
		deleteallnotes: [['m', 'M4 5h16v11H9l-5 4z'], ['a', 'M9 8l6 5 M15 8l-6 5']],
		showannotations: [['m', 'M4 5h16v11H9l-5 4z'], ['a', 'M8 9h8 M8 12h5']],
		showtrackedchanges: [['m', 'M2 12s4-6 10-6 10 6 10 6-4 6-10 6S2 12 2 12z'], ['a', 'M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6']],
		previoustrackedchange: [['m', 'M4 7h16 M4 12h10 M4 17h8'], ['a', 'M17 16l2-2 2 2']],
		nexttrackedchange: [['m', 'M4 7h16 M4 12h10 M4 17h8'], ['a', 'M17 14l2 2 2-2']],
		acceptalltrackedchanges: [['m', 'M4 7h9 M4 12h7 M4 17h5'], ['a', 'M13 15l3 3 6-7']],
		comparedocuments: [['m', 'M4 4h7v16H4z M13 4h7v16h-7z'], ['a', 'M7 9h1 M16 9h1 M7 13h1 M16 13h1']],
		protecttracechangemode: [['m', 'M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z'], ['a', 'M9 12l2 2 4-4']],
		showboundaries: [['m', 'M5 3h14v18H5z'], ['a', 'M8 6h8v12H8z']],
		multipageview: [['m', 'M3 5h8v14H3z M13 5h8v14h-8z']],
		sidebar: [['m', 'M3 4h18v16H3z'], ['a', 'M15 4v16']],
		eurisezoom100: [['m', 'M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14 M16 16l4 4'], ['a', 'M9 9h4v4H9z']],
		eurisezoomwidth: [['m', 'M3 12h18'], ['a', 'M6 9l-3 3 3 3 M18 9l3 3-3 3']],
		eurisenotte: [['m', 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z']],
		eurisereplace: [['m', 'M4 7h11 M20 17H9'], ['a', 'M12 4l3 3-3 3 M12 14l-3 3 3 3']],
		selectall: [['m', 'M4 4h16v16H4z'], ['a', 'M8 8h8v8H8z']],
		pastespecial: 'paste',
		changecaserotatecase: [['m', 'M3 18l4-11 4 11 M4.5 14h5'], ['a', 'M14 12a2.5 2.5 0 1 0 5 0v6 M19 12a2.5 2.5 0 1 1-5 0']],
		outlinebullet: [['m', 'M9 6h11 M12 12h8 M15 18h5'], ['a', 'M4 6h1 M7 12h1 M10 18h1']],
		cellverttop: 'aligntop',
		cellvertcenter: 'alignverticalcenter',
		cellvertbottom: 'alignbottom',
		insertcalctable: 'inserttable',
		defineprintarea: [['m', 'M6 3h12v18H6z'], ['a', 'M9 7h6v8H9z']],
		editheaderandfooter: 'insertpageheader',
		headerandfooter: 'insertpageheader',
		toggleprintgrid: 'togglesheetgrid',
		calculatehard: 'calculate',
		goalseekdialog: [['m', 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18'], ['a', 'M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8 M12 11.5v1']],
		solverdialog: [['m', 'M4 6h16 M4 12h16 M4 18h16'], ['a', 'M8 4v4 M16 10v4 M11 16v4']],
		definedbname: 'definename',
		datafilterremovefilter: [['m', 'M4 5h16l-6 7v6l-4 2v-8z'], ['a', 'M16 16l4 4 M20 16l-4 4']],
		texttocolumns: [['m', 'M4 4h7v16H4z M13 4h7v16h-7z'], ['a', 'M6 9h3 M15 9h3']],
		handleduplicaterecords: [['m', 'M8 8h12v12H8z'], ['a', 'M4 4h12v4 M4 4v12h4']],
		group: [['m', 'M4 4h16v16H4z'], ['a', 'M8 9h8 M8 13h8']],
		ungroup: 'formatungroup',
		showdetail: [['m', 'M4 4h16v16H4z'], ['a', 'M12 8v8 M8 12h8']],
		hidedetail: [['m', 'M4 4h16v16H4z'], ['a', 'M8 12h8']],
		freezepanesrow: 'freezepanes',
		freezepanescolumn: 'freezepanes',
		eurisehighlight: [['m', 'M4 4h16v16H4z'], ['f', 'M10 4h4v16h-4z M4 10h16v4H4z']],
		euriselayout: [['m', 'M3 5h18v14H3z'], ['a', 'M6 8h12 M6 12h5v4H6z M14 12h4 M14 15h4']],
		insertavmedia: [['m', 'M3 5h18v14H3z'], ['a', 'M10 9l5 3-5 3z']],
		insertslidefield: 'insertpagenumberfield',
		eurisetransizione: [['m', 'M3 6h8v12H3z'], ['a', 'M13 6h8v12h-8z M9 12h6']],
		euriseanimazione: [['m', 'M12 3l2.5 6 6.5.5-5 4.5 1.5 6.5L12 17l-5.5 3.5L8 14 3 9.5 9.5 9z']],
		eurisepresentafinestra: [['m', 'M3 4h18v14H3z'], ['a', 'M8 8h8v6H8z']],
		euriseconsole: [['m', 'M3 4h12v10H3z M17 8h4v10h-8v-4'], ['a', 'M7 18h4']],
		hideslide: [['m', 'M3 5h18v12H3z'], ['a', 'M4 4l16 14']],
		showslide: [['m', 'M3 5h18v12H3z'], ['a', 'M9 11l2 2 4-4']],
		insert: [['m', 'M4 4h16v16H4z'], ['a', 'M12 8v8 M8 12h8']],
		zoomreset: 'eurisezoom100',
		euriseritaglia: [['m', 'M6 2v14h14'], ['a', 'M2 6h14v14']],
		euriserifletti: [['m', 'M12 3v18'], ['a', 'M9 7L4 17h5z M15 7l5 10h-5z']],
		euriselinea: [['m', 'M4 20L20 4'], ['a', 'M4 14l6 6']],
		euriseconcentrazione: [['m', 'M4 8V4h4 M16 4h4v4 M20 16v4h-4 M8 20H4v-4'], ['a', 'M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4']],
		eurisenastro: [['m', 'M3 5h18v6H3z'], ['a', 'M8 16l4 4 4-4']],
		euriseinfo: [['m', 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18'], ['a', 'M12 11v6 M12 7.5v.01']],
		setdocumentproperties: 'euriseinfo',
		wrapoff: [['m', 'M4 5h16 M4 19h16'], ['a', 'M8 9h8v6H8z']],
		wrapon: 'eurisewrap',
		wrapideal: 'eurisewrap',
		wrapleft: [['m', 'M4 6h16 M4 18h16 M13 10h7 M13 14h7'], ['a', 'M4 10h7v4H4z']],
		wrapright: [['m', 'M4 6h16 M4 18h16 M4 10h7 M4 14h7'], ['a', 'M13 10h7v4h-7z']],
		wrapthrough: [['m', 'M4 6h16 M4 10h16 M4 14h16 M4 18h16'], ['a', 'M8 8h8v8H8z']],
		pagemargin: 'eurisepagemargin',
		pagesize: 'eurisepagesize',
		line: 'euriselinea',
		insertfield: 'insertfieldctrl',
		insertpagecountfield: 'pagenumberwizard',
		inserttitlefield: 'drawtext',
		comparechanges: 'comparedocuments',
		searchprev: [['m', 'M6 15l6-6 6 6']],
		searchnext: [['m', 'M6 9l6 6 6-6']],
		prev: 'searchprev',
		next: 'searchnext',
		downloadas: [['m', 'M12 3v12 M7 10l5 5 5-5'], ['a', 'M5 20h14']],
		settings: [['m', 'M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6'], ['a', 'M12 2v3 M12 19v3 M4.2 4.2l2.1 2.1 M17.7 17.7l2.1 2.1 M2 12h3 M19 12h3 M4.2 19.8l2.1-2.1 M17.7 6.3l2.1-2.1']],
		spellcheck: 'spellingandgrammardialog',
		behindobject: 'sendtoback',
		// Nomi con cui Collabora chiede alcune di queste icone (iconURLAliases di LOUtil).
		insertsymbol: 'charmapcontrol',
		insertrowsmenu: 'insertrowsafter',
		insertcolumnsmenu: 'insertcolumnsafter',
		drawchart: 'insertobjectchart',
		acceptchanges: 'accepttrackedchanges',
		inserthyperlink: 'hyperlinkdialog',
		fillcolor: 'backgroundcolor',
		datefield: 'insertdatefield',
		shownote: 'insertannotation',
		leftindent: 'incrementindent'
	};

	var ICON_CACHE = {};
	/** Immagine in linea → nome dell'icona: serve a ridisegnare le icone già sullo schermo quando cambia l'accento. */
	var ICON_OF = {};

	// Colore d'accento dei dettagli delle icone (richiesta del proprietario, 14 novembre): il tratto resta blu Notte, i
	// dettagli prendono l'accento della suite. Sul telefono e sul tablet lo manda Office (come la Scrivania o la scelta
	// nelle impostazioni di Office); sul computer si sceglie dal menu File e resta nel browser del guscio.
	var ACCENT = { light: '#3E5C8A', dark: '#A9C1F2' };
	// Tinte della suite (CalendarTint, senza Grafite) e quelle in più della Scrivania (ExtraTint): giorno e sera.
	var ACCENTS = [
		['Blu notte', 'Night blue', '#3E5C8A', '#8FA9D9'], ['Salvia', 'Sage', '#4F6B5A', '#93BBA0'],
		['Ottone', 'Brass', '#85672B', '#D2B06A'], ['Borgogna', 'Burgundy', '#8C2F39', '#DE919A'],
		['Petrolio', 'Petrol', '#2E6E73', '#79BCC1'], ['Prugna', 'Plum', '#6B4A7A', '#BA9BCB'],
		['Terracotta', 'Terracotta', '#A0522D', '#DE9A72'], ['Arancio', 'Orange', '#B8621E', '#F2A866'],
		['Viola', 'Violet', '#5A4BA0', '#B3A6F0'], ['Cuoio', 'Leather', '#7A4A2A', '#C9956B'],
		['Verde inglese', 'British green', '#1F4D3A', '#7FB59A'], ['Azzurro costiera', 'Coast blue', '#1E78A8', '#7CC4EA']
	];

	function darkTheme() {
		try { return !!(window.prefs && window.prefs.getBoolean && window.prefs.getBoolean('darkTheme')); } catch (e) { return false; }
	}

	/** Cambia l'accento delle icone e ridisegna quelle già sullo schermo. */
	function setAccent(light, dark) {
		if (!/^#[0-9a-f]{6}$/i.test(light || '')) return;
		ACCENT = { light: light, dark: /^#[0-9a-f]{6}$/i.test(dark || '') ? dark : light };
		ICON_CACHE = {};
		var old = ICON_OF;
		ICON_OF = {};
		var night = darkTheme();
		Array.prototype.forEach.call(document.querySelectorAll('img'), function (img) {
			var name = old[img.getAttribute('src')];
			if (name) img.src = euriseIcon(name, night);
		});
	}

	/** L'icona nostra come immagine SVG in linea, oppure null se non l'abbiamo. */
	function euriseIcon(file, dark) {
		var key = String(file || '').split('/').pop().replace(/\.svg$/, '').replace(/^(lc|sc|cmd)_/, '').toLowerCase();
		var spec = ICONS[key];
		if (typeof spec === 'string') spec = ICONS[spec];
		if (!spec) return null;
		var cacheKey = key + (dark ? ':d' : ':l');
		if (ICON_CACHE[cacheKey]) return ICON_CACHE[cacheKey];
		var main = dark ? '#E6E9F0' : '#1F2A44';
		var accent = dark ? ACCENT.dark : ACCENT.light;
		var body = spec.map(function (layer) {
			var style = layer[0];
			if (style === 'f') return '<path d="' + layer[1] + '" fill="' + accent + '" fill-opacity="0.18" stroke="none"/>';
			if (style.charAt(0) === '*') return '<path d="' + layer[1] + '" fill="' + style.slice(1) + '" stroke="none"/>';
			var color = style === 'm' ? main : style === 'a' ? accent : style;
			return '<path d="' + layer[1] + '" stroke="' + color + '"/>';
		}).join('');
		var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke-width="1.7" ' +
			'stroke-linecap="round" stroke-linejoin="round">' + body + '</svg>';
		ICON_CACHE[cacheKey] = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
		ICON_OF[ICON_CACHE[cacheKey]] = key;
		return ICON_CACHE[cacheKey];
	}

	/** Icone chieste e non nostre: la prova le elenca per completare il disegno. */
	var missingIcons = {};

	(function useEuriseIcons(tries) {
		var util = window.app && window.app.LOUtil;
		if (!util || typeof util.getImageURL !== 'function') {
			if (tries > 0) setTimeout(function () { useEuriseIcons(tries - 1); }, 50);
			return;
		}
		if (util.getImageURL.eurise) return;
		var original = util.getImageURL;
		var ours = function (name) {
			var mine = euriseIcon(name, darkTheme());
			if (mine) return mine;
			if (/^(lc|sc)_/.test(String(name))) missingIcons[name] = true;
			return original.apply(this, arguments);
		};
		ours.eurise = true;
		util.getImageURL = ours;
	})(200);

	var SUITE_TAB = 'Suite';

	// --- Ponte con il guscio dell'app -------------------------------------------------------------------------

	var pending = {};
	var nextId = 1;

	/** L'origine della pagina che ospita l'editor (dal referrer), o null se non si sa: allora non si manda nulla. */
	function hostOrigin() {
		try {
			return document.referrer ? new URL(document.referrer).origin : null;
		} catch (e) {
			return null;
		}
	}

	function toShell(message, onReply) {
		var id = nextId++;
		message.id = id;
		if (onReply) pending[id] = onReply;
		// Nel guscio Linux di Collabora (coda-qt) i messaggi sconosciuti andrebbero al motore: lì il canale Eurise non c'è ancora.
		if (window.ThisIsAMobileApp && window.postMobileMessage && !window.ThisIsTheQtApp) {
			window.postMobileMessage('EURISE ' + JSON.stringify(message));
		} else if (window.parent && window.parent !== window && hostOrigin()) {
			// Sul computer (guscio futuro) o in una pagina che ospita l'editor: solo all'origine di chi ci ospita, mai a «*»
			// (una pagina estranea che incornicia l'editor non legge i messaggi; CodeQL js/cross-window-information-leak).
			window.parent.postMessage(JSON.stringify({ MessageId: 'Eurise', Values: message }), hostOrigin());
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
		if (wopi && wopi.BaseFileName) return wopi.BaseFileName;
		// Nell'app (Android) il file arriva come file_path nell'indirizzo della pagina: il nome è l'ultimo pezzo.
		var path = '';
		try { path = new URLSearchParams(window.location.search).get('file_path') || ''; } catch (e) { path = ''; }
		try { path = decodeURIComponent(path); } catch (e) { /* già decodificato */ }
		return path.split('/').pop() || '';
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
		if (onDesk()) {
			// Sul computer anche tutte le voci dei nostri nastri, con la scheda e la scorciatoia (proposta 5).
			Object.keys(DESK_ITEMS).forEach(function (label) {
				var entry = DESK_ITEMS[label];
				var key = shortcutOf(entry.item);
				if (seen['desk:' + label]) return;
				seen['desk:' + label] = true;
				out.push({ label: label, command: entry.item, where: entry.where + (key ? ' · ' + key : '') });
			});
		}
		if (typeof _UNO === 'function') COMMON.forEach(function (c) { add(_UNO(c, app.map.getDocType(), true), c, ''); });
		return out;
	}

	function runCommand(command) {
		closeOverlay();
		if (command && typeof command === 'object') {
			countUse(command);
			if (typeof command.run === 'string') uno(command.run); else command.run();
			return;
		}
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
			var counts = onDesk() ? useCounts() : {};
			found.sort(function (a, b) {
				return (fold(a.label).indexOf(fq) - fold(b.label).indexOf(fq)) || ((counts[b.label] || 0) - (counts[a.label] || 0));
			});
			if (found.length) heading(!q && onDesk() && Object.keys(counts).length ? t('Più usati', 'Most used') : t('Comandi', 'Commands'));
			found.slice(0, q ? 8 : 6).forEach(function (c) {
				row(c.label, '', c.where, 0, function () { runCommand(c.command); });
			});
			if (q) {
				// Tavola OffCerca: prima il documento aperto, poi la suite, il web solo se lo si sceglie.
				heading(t('Nel documento', 'In the document'));
				row(t('Trova', 'Find') + ' «' + q + '»', t('evidenzia e passa al successivo', 'highlight and go to the next one'), '', 0, function () {
					closeOverlay();
					findInDocument(q);
				});
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

	/** Cerca nel documento con la barra di ricerca di Collabora (successivo, precedente, chiudi). */
	function findInDocument(q) {
		dispatch('showsearchbar');
		setTimeout(function () {
			var input = document.getElementById('search-input');
			if (input) {
				input.value = q;
				input.dispatchEvent(new Event('input', { bubbles: true }));
			}
			if (app.map.search) app.map.search(q);
		}, 80);
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

	// Le schede della dock e i loro gruppi vengono dal nastro del tablet (phoneTabs, più sotto).

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
		// Il nome arriva con le informazioni del file, a volte dopo che l'intestazione è già sullo schermo.
		var name = document.querySelector('#eurise-header .eurise-name');
		if (name && !name.textContent) name.textContent = documentName();
		var el = document.querySelector('#eurise-header .eurise-status');
		if (el) el.textContent = phone.saved ? t('Salvato', 'Saved') : t('Modifiche in corso…', 'Editing…');
		// Sul computer accanto allo stato c'è la cartella (tavola OffComputer: «Salvato · Murena › Lavoro»).
		if (el && onDesk()) {
			var parts = documentPath().split('/').filter(Boolean);
			if (parts.length > 1) el.textContent += ' · ' + parts[parts.length - 2];
		}
		if (el) el.classList.toggle('pending', !phone.saved);
	}

	/**
	 * Salva da sé poco dopo l'ultima modifica, con lo stesso messaggio del salvataggio automatico dell'app di Collabora
	 * (LOActivity.onPause: save dontTerminateEdit=1 dontSaveIfUnmodified=1), non con il comando .uno:Save del menu.
	 */
	function scheduleSave() {
		clearTimeout(phone.saveTimer);
		phone.saveTimer = setTimeout(function () { if (!phone.saved) saveNow(true); }, 8000);
	}

	/**
	 * Salva con il messaggio «save». Mai uno('.uno:Save'): il motore lo rifiuta con un'asserzione
	 * (DocumentBroker::forwardToChild) e si chiude, come è successo sul telefono il 14 novembre.
	 */
	function saveNow(onlyIfModified) {
		var message = 'save dontTerminateEdit=1' + (onlyIfModified ? ' dontSaveIfUnmodified=1' : '');
		if (app.socket && app.socket.sendMessage) app.socket.sendMessage(message);
		else if (app.map.save) app.map.save(true, !!onlyIfModified);
	}

	function phoneDock(docType) {
		if (document.getElementById('eurise-dock')) return;
		var tabs = phoneTabs(docType);
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
		// Tavola OffTelefonoGruppi: sopra la dock le pastiglie dei gruppi della scheda e, sotto, i gruppi del nastro
		// come card che scorrono di lato; la card seguente si intravede a destra.
		var panel = document.createElement('div');
		panel.id = 'eurise-panel';
		panel.className = 'groups';
		panel.setAttribute('role', 'dialog');
		panel.setAttribute('aria-label', tab.label);
		var pills = document.createElement('div');
		pills.className = 'eurise-gpills';
		pills.setAttribute('role', 'tablist');
		pills.setAttribute('aria-label', t('Gruppi di ', 'Groups of ') + tab.label);
		var track = document.createElement('div');
		track.className = 'eurise-cards';
		var cards = [];
		function mark(index) {
			Array.prototype.forEach.call(pills.children, function (p, i) {
				p.classList.toggle('active', i === index);
				p.setAttribute('aria-selected', i === index ? 'true' : 'false');
			});
		}
		tab.groups.forEach(function (group, i) {
			var card = phoneCard(group);
			cards.push(card);
			track.appendChild(card);
			var pill = document.createElement('button');
			pill.type = 'button';
			pill.setAttribute('role', 'tab');
			pill.className = 'eurise-gpill' + (group.suite ? ' suite' : '');
			pill.textContent = group.label;
			pill.onclick = function () {
				mark(i);
				track.scrollTo({ left: card.offsetLeft - track.firstChild.offsetLeft, behavior: 'smooth' });
			};
			pills.appendChild(pill);
		});
		var ticking = false;
		track.addEventListener('scroll', function () {
			if (ticking) return;
			ticking = true;
			window.requestAnimationFrame(function () {
				ticking = false;
				var base = track.firstChild.offsetLeft;
				var best = 0;
				cards.forEach(function (c, i) {
					if (Math.abs(c.offsetLeft - base - track.scrollLeft) < Math.abs(cards[best].offsetLeft - base - track.scrollLeft)) best = i;
				});
				// In fondo l'ultima card non arriva a sinistra: conta come scelta.
				if (track.scrollLeft + track.clientWidth >= track.scrollWidth - 2) best = cards.length - 1;
				mark(best);
			});
		}, { passive: true });
		if (tab.groups.length > 1) panel.appendChild(pills);
		panel.appendChild(track);
		document.body.appendChild(panel);
		phone.panel = panel;
		mark(0);
	}

	/** Un gruppo del nastro come card del telefono: tendine del carattere in alto, pulsanti da 44, etichetta in basso. */
	function phoneCard(group) {
		var card = document.createElement('section');
		card.className = 'eurise-card' + (group.suite ? ' suite' : '');
		card.setAttribute('aria-label', group.label);
		if (group.fonts) card.appendChild(fontRow(true));
		var keys = document.createElement('div');
		keys.className = 'eurise-cardkeys';
		group.items.forEach(function (item) { keys.appendChild(cardButton(item, group.suite)); });
		card.appendChild(keys);
		card.appendChild(groupFoot(group));
		return card;
	}

	function cardButton(item, inSuite) {
		var b = document.createElement('button');
		b.type = 'button';
		b.title = item.label;
		b.setAttribute('aria-label', item.label);
		var picture = item.glyph ? null : commandIcon(item.icon || item.run, 22);
		var withLabel = item.big || (!picture && !item.glyph);
		b.className = 'eurise-ck' + (withLabel ? ' big' : '') + (inSuite ? ' suite' : '') + (picture ? '' : ' plain');
		if (item.glyph) {
			var glyph = document.createElement('span');
			glyph.className = 'eurise-glyph g-' + item.glyph.replace(/[^a-z]/gi, '');
			glyph.textContent = item.glyph;
			b.appendChild(glyph);
		} else if (picture) b.appendChild(picture);
		if (item.swatch) b.appendChild(swatch(item.swatch));
		if (withLabel) {
			var span = document.createElement('span');
			span.textContent = item.label;
			b.appendChild(span);
		}
		if (typeof item.run === 'string') {
			b.setAttribute('data-uno', item.run);
			if (phone.states[item.run] === 'true') b.classList.add('on');
		}
		b.onclick = function () { if (typeof item.run === 'string') uno(item.run); else item.run(); };
		return b;
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
		// Carattere e dimensione mostrati nelle tendine (telefono e tablet).
		Array.prototype.forEach.call(document.querySelectorAll('[data-state="' + name + '"]'), function (el) {
			el.textContent = shownState(name);
		});
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
		if (!map || !window.mode || !smallScreen() || window.ThisIsTheQtApp) return;
		document.body.classList.add('eurise-tel');
		editAtOnce(map);
		function build() {
			// Un PDF aperto nel motore ha già le sue schede Annota e Pagine: lì resta l'interfaccia di Collabora.
			if (isPdf()) { document.body.classList.remove('eurise-tel'); return; }
			phoneHeader();
			phoneDock(map.getDocType());
		}
		if (map._docLayer) build(); else map.on('doclayerinit', build);
		map.on('docloaded wopiprops', status);
		map.on('commandstatechanged', onState);
		// Il pannello si chiude da solo quando si apre un dialogo o il menu di Collabora.
		map.on('mobilewizard closealldialogs', closePanel);
	}

	/**
	 * La mappa c'è e window.mode sa già dire che schermo è: nel guscio Linux di Collabora (26.04) window.mode esiste
	 * prima delle sue funzioni, e il nostro script parte subito dopo bundle.js.
	 */
	/** Tappe dell'avvio, lette dalla prova sul computer (window.euriseOffice.trace). */
	var trace = [];

	function ready() {
		var m = window.mode;
		return !!(window.app && app.map && app.map.on && m && (typeof m.isMobile === 'function' || typeof m.isSmallScreenDevice === 'function') &&
			typeof m.isTablet === 'function' && typeof m.isDesktop === 'function');
	}

	/** Schermo da telefono: isMobile in Collabora 25.04, isSmallScreenDevice nella 26.04 (guscio Linux). */
	function smallScreen() {
		var m = window.mode;
		return typeof m.isMobile === 'function' ? m.isMobile() : m.isSmallScreenDevice();
	}

	(function waitForMap(tries) {
		if (ready()) { setupPhone(); return; }
		if (tries > 0) setTimeout(function () { waitForMap(tries - 1); }, 100);
	})(300);

	// --- Tablet: barra a nastro Eurise (tavola OffTablet) --------------------------------------------------------
	//
	// Sul tablet Collabora mostra un menu classico, la modalità «Viewing» e la matita. Qui, nella riga della barra
	// di Collabora: un'intestazione a scheda (nome e stato, annulla e ripeti, «Cerca un comando» con Ctrl K,
	// Condividi), le schede e, sotto, il nastro della scheda scelta con i gruppi in riquadri e l'etichetta in basso.
	// Icone dei comandi del motore (temi chiaro e scuro). Il documento si apre già modificabile (editAtOnce).

	/** Voce del nastro: [it, en, comando .uno o funzione, 'big' per il pulsante grande]. */
	function r(it, en, run, big) { return { label: t(it, en), run: run, big: big === 'big' }; }
	/** Gruppo: titolo, voci, comando «altre opzioni» (facoltativo). */
	function g(it, en, items, more) { return { label: t(it, en), items: items, more: more }; }

	/** Il telefono mostra il segno (B, I, x²…) al posto dell'icona del motore. */
	function glyph(item, text) { item.glyph = text; return item; }
	/** Voce che esegue una funzione ma prende l'icona di un comando del motore. */
	function ri(it, en, run, big, iconCommand) { var item = r(it, en, run, big); item.icon = iconCommand; return item; }
	/** Sul computer la voce piccola mostra anche il nome (Taglia, Copia… come nella tavola OffComputer). */
	function lab(item) { item.desk = 'lab'; return item; }
	/** Nome più corto sul computer, dove la voce mostra il nome accanto all'icona. */
	function deskLabel(item, it, en) { item.deskLabel = t(it, en); return item; }
	/** Icona disegnata da noi (comandi della Suite, che il motore non conosce). */
	function drawn(item, path) { item.svg = path; return item; }

	/** Computer (guscio Linux): barra in una riga, nastro compatto, colonna Properties con le nostre sezioni. */
	function onDesk() { return document.body.classList.contains('eurise-desk'); }

	var SUITE_ICONS = {
		detta: 'M9 4.6a3 3 0 0 1 6 0V11a3 3 0 0 1-6 0Z M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21',
		traduci: 'M3 5h9 M7.5 3v2 M5 5c1 4 3.5 6.5 6.5 7.5 M10 5c-1 4-3.5 6.5-6.5 7.5 M13 21l4-9 4 9 M14.4 18h5.2',
		dafare: 'M4 5h16v14H4z M8 12l3 3 5-6',
		evento: 'M4 6h16v14H4z M4 10h16 M8 3v4 M16 3v4',
		note: 'M5 4h10l4 4v12H5z M15 4v4h4 M8 13h8 M8 17h5',
		viaggio: 'M3 17h18 M5 17l2-6h10l2 6 M8 11l1.5-4h5L16 11 M7 20a1 1 0 1 0 0 .01 M17 20a1 1 0 1 0 0 .01',
		grafico: 'M4 20h16 M7 16v-5 M12 16V6 M17 16v-8'
	};

	// --- Colori: tinte della suite, comando giusto per ciascun tipo di documento (come Widget.ColorPickerButton) ---
	var COLORS = {
		font: [[t('Automatico', 'Automatic'), -1], [t('Nero', 'Black'), 0x1E1D1B], [t('Grafite', 'Graphite'), 0x56564F],
			[t('Notte', 'Night'), 0x1F2A44], [t('Blu', 'Blue'), 0x3E5C8A], [t('Verde', 'Green'), 0x3F6E52],
			[t('Mattone', 'Brick'), 0xB4441A], [t('Vino', 'Wine'), 0x7A3440], [t('Ocra', 'Ochre'), 0x8A6A1F]],
		back: [[t('Nessuno', 'None'), -1], [t('Sabbia', 'Sand'), 0xF2D98B], [t('Cielo', 'Sky'), 0xDDE3F0],
			[t('Salvia', 'Sage'), 0xCFE3D6], [t('Rosa', 'Rose'), 0xF3D3D8], [t('Pesca', 'Peach'), 0xF6D9C4],
			[t('Perla', 'Pearl'), 0xE4E3DE], [t('Lavanda', 'Lavender'), 0xE2DCF0], [t('Menta', 'Mint'), 0xD5EEE6]]
	};
	var lastColor = { font: 0xB4441A, back: 0xF2D98B };

	function colorCommand(kind) {
		var type = app.map.getDocType();
		if (kind === 'back') return type === 'spreadsheet' ? '.uno:BackgroundColor' : '.uno:CharBackColor';
		return type === 'text' ? '.uno:FontColor' : '.uno:Color';
	}

	function hex(value) { return value < 0 ? 'transparent' : '#' + ('00000' + value.toString(16)).slice(-6); }

	function swatch(kind) {
		var bar = document.createElement('span');
		bar.className = 'eurise-swatch';
		bar.setAttribute('data-swatch', kind);
		bar.style.background = hex(lastColor[kind]);
		return bar;
	}

	function pickColor(kind) {
		return function () {
			if (onDesk()) { deskColors(kind); return; }
			var box = overlay('eurise-colors');
			var h = document.createElement('h2');
			h.textContent = kind === 'back' ? (app.map.getDocType() === 'spreadsheet' ? t('Riempimento', 'Fill') : t('Evidenzia', 'Highlight')) : t('Colore del testo', 'Font color');
			box.appendChild(h);
			var grid = document.createElement('div');
			grid.className = 'eurise-swatches';
			COLORS[kind].forEach(function (c) {
				var b = document.createElement('button');
				b.type = 'button';
				b.className = 'eurise-sw' + (c[1] < 0 ? ' none' : '') + (c[1] === lastColor[kind] ? ' on' : '');
				b.innerHTML = '<span></span>';
				b.firstChild.style.background = hex(c[1]);
				b.appendChild(document.createTextNode(c[0]));
				b.onclick = function () {
					closeOverlay();
					var command = colorCommand(kind);
					var params = {};
					params[command.replace('.uno:', '') + '.Color'] = { type: 'long', value: c[1] };
					uno(command, params);
					if (c[1] >= 0) {
						lastColor[kind] = c[1];
						Array.prototype.forEach.call(document.querySelectorAll('[data-swatch="' + kind + '"]'), function (el) { el.style.background = hex(c[1]); });
					}
				};
				grid.appendChild(b);
			});
			box.appendChild(grid);
		};
	}

	function colorItem(it, en, kind, iconCommand) {
		var item = ri(it, en, pickColor(kind), false, iconCommand);
		item.swatch = kind;
		return item;
	}

	// --- Carattere e dimensione: elenco dei caratteri dal motore (commandvalues), applicati come la barra di Collabora ---
	var FONT_SIZES = ['6', '7', '8', '9', '10', '10.5', '11', '12', '13', '14', '15', '16', '18', '20', '22', '24', '26', '28',
		'32', '36', '40', '44', '48', '54', '60', '66', '72', '80', '88', '96'];

	function shownState(name) {
		var state = phone.states[name] || '';
		if (name === '.uno:FontHeight') return state && state !== '0' ? state.replace(/\.0$/, '') : '–';
		return state || t('Carattere', 'Font');
	}

	function fontNames() {
		var values = app.map.getToolbarCommandValues ? app.map.getToolbarCommandValues('.uno:CharFontName') : null;
		if (!values || typeof values !== 'object') return [];
		return Object.keys(values).filter(Boolean).sort(function (a, b) { return a.localeCompare(b); });
	}

	function chooseFont() {
		if (onDesk()) { deskFonts(); return; }
		var box = overlay('eurise-fonts');
		var h = document.createElement('h2');
		h.textContent = t('Carattere', 'Font');
		box.appendChild(h);
		var field = document.createElement('input');
		field.type = 'search';
		field.placeholder = t('Cerca un carattere', 'Search a font');
		field.setAttribute('aria-label', field.placeholder);
		box.appendChild(field);
		var list = document.createElement('div');
		list.className = 'eurise-fontlist';
		box.appendChild(list);
		var current = phone.states['.uno:CharFontName'] || '';
		function render() {
			var names = fontNames();
			var q = fold(field.value.trim());
			list.innerHTML = '';
			if (!names.length) {
				var wait = document.createElement('p');
				wait.className = 'eurise-wait';
				wait.textContent = t('Carico i caratteri…', 'Loading fonts…');
				list.appendChild(wait);
				return;
			}
			names.filter(function (n) { return !q || fold(n).indexOf(q) >= 0; }).slice(0, 300).forEach(function (name) {
				var b = document.createElement('button');
				b.type = 'button';
				b.className = 'eurise-row' + (name === current ? ' on' : '');
				b.textContent = name;
				b.onclick = function () { closeOverlay(); app.map.applyFont(name); };
				list.appendChild(b);
			});
		}
		field.oninput = render;
		render();
		if (!fontNames().length) {
			app.map.once('updatetoolbarcommandvalues', render);
			if (app.socket && app.socket.sendMessage) app.socket.sendMessage('commandvalues command=.uno:CharFontName');
		}
	}

	function chooseSize() {
		if (onDesk()) { deskSizes(); return; }
		var box = overlay('eurise-sizes');
		var h = document.createElement('h2');
		h.textContent = t('Dimensione', 'Size');
		box.appendChild(h);
		var grid = document.createElement('div');
		grid.className = 'eurise-sizegrid';
		var current = shownState('.uno:FontHeight');
		FONT_SIZES.forEach(function (size) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-size' + (size === current ? ' on' : '');
			b.textContent = size;
			b.onclick = function () { closeOverlay(); app.map.applyFontSize(size); };
			grid.appendChild(b);
		});
		box.appendChild(grid);
	}

	function clearFormatting() {
		var type = app.map.getDocType();
		uno(type === 'presentation' || type === 'drawing' ? '.uno:SetDefault' : '.uno:ResetAttributes');
	}

	var DROP = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 10l5 5 5-5"></path></svg>';

	/** Tendine di carattere e dimensione; sul telefono anche «Togli la formattazione». */
	function fontRow(onPhone, extras) {
		var row = document.createElement('div');
		row.className = 'eurise-fontrow';
		function drop(cls, command, label, run) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-drop ' + cls;
			b.setAttribute('aria-label', label);
			b.title = label;
			b.innerHTML = '<span data-state="' + command + '"></span>' + DROP;
			b.firstChild.textContent = shownState(command);
			b.onclick = function () { anchorEl = b; run(); };
			row.appendChild(b);
		}
		drop('name', '.uno:CharFontName', t('Carattere', 'Font'), chooseFont);
		drop('size', '.uno:FontHeight', t('Dimensione', 'Size'), chooseSize);
		if (extras) extras.forEach(function (item) { row.appendChild(ribbonButton(item, false)); });
		if (onPhone) {
			var clear = document.createElement('button');
			clear.type = 'button';
			clear.className = 'eurise-ck';
			clear.title = t('Togli la formattazione', 'Clear formatting');
			clear.setAttribute('aria-label', clear.title);
			clear.innerHTML = icon('M5 19L11 5l4 9 M15 15l5 5 M20 15l-5 5', 20);
			clear.onclick = clearFormatting;
			row.appendChild(clear);
		}
		return row;
	}

	/** Gruppo con voci diverse sul computer. */
	function deskItems(group, items) { group.deskItems = items; return group; }

	var style = function (name) {
		return function () { uno('.uno:StyleApply', { Style: { type: 'string', value: name }, FamilyName: { type: 'string', value: 'ParagraphStyles' } }); };
	};
	var APPUNTI = g('Appunti', 'Clipboard', [r('Incolla', 'Paste', '.uno:Paste', 'big'), lab(r('Taglia', 'Cut', '.uno:Cut')),
		lab(r('Copia', 'Copy', '.uno:Copy')), deskLabel(lab(r('Copia formato', 'Clone formatting', '.uno:FormatPaintbrush')), 'Formato', 'Format')]);
	var CARATTERE = g('Carattere', 'Font', [glyph(r('Grassetto', 'Bold', '.uno:Bold'), 'B'), glyph(r('Corsivo', 'Italic', '.uno:Italic'), 'I'),
		glyph(r('Sottolineato', 'Underline', '.uno:Underline'), 'U'), glyph(r('Barrato', 'Strikethrough', '.uno:Strikeout'), 'S'),
		glyph(r('Apice', 'Superscript', '.uno:SuperScript'), 'x²'), glyph(r('Pedice', 'Subscript', '.uno:SubScript'), 'x₂'),
		colorItem('Colore del testo', 'Font color', 'font', '.uno:Color'), colorItem('Evidenzia', 'Highlight', 'back', '.uno:CharBackColor')], '.uno:FontDialog');
	CARATTERE.fonts = true;
	var STILI = g('Stili', 'Styles', [r('Testo', 'Text', style('Standard'), 'big'), r('Titolo 1', 'Heading 1', style('Heading 1'), 'big'),
		r('Titolo 2', 'Heading 2', style('Heading 2'), 'big'), r('Citazione', 'Quotation', style('Quotations'), 'big')]);
	STILI.tiles = true;
	STILI.deskItems = STILI.items.slice(0, 3);
	STILI.deskMore = '.uno:SidebarDeck.StyleListDeck';
	var DIAPOSITIVE = g('Diapositive', 'Slides', [r('Nuova', 'New', '.uno:InsertPage', 'big'), r('Duplica', 'Duplicate', '.uno:DuplicatePage'),
		r('Elimina', 'Delete', '.uno:DeletePage')]);
	var PARAGRAFO = g('Paragrafo', 'Paragraph', [r('Elenco', 'Bullets', '.uno:DefaultBullet'), r('Numeri', 'Numbering', '.uno:DefaultNumbering'),
		r('A sinistra', 'Left', '.uno:LeftPara'), r('Al centro', 'Center', '.uno:CenterPara'), r('A destra', 'Right', '.uno:RightPara'),
		r('Giustificato', 'Justified', '.uno:JustifyPara'), r('Riduci rientro', 'Decrease indent', '.uno:DecrementIndent'),
		r('Aumenta rientro', 'Increase indent', '.uno:IncrementIndent')], '.uno:ParagraphDialog');
	// Sul computer Detta e Traduci restano grandi, le altre voci piccole con il nome (gruppo stretto, tavola OffComputer).
	var SUITE = g('Suite', 'Suite', [
		lab(drawn(r('Da fare', 'To-do', function () { actions['eurise-dafare'](); }, 'big'), SUITE_ICONS.dafare)),
		lab(drawn(r('Evento', 'Event', function () { actions['eurise-evento'](); }, 'big'), SUITE_ICONS.evento)),
		lab(drawn(r('Note', 'Notes', function () { actions['eurise-note'](); }, 'big'), SUITE_ICONS.note)),
		drawn(r('Traduci', 'Translate', function () { actions['eurise-traduci'](); }, 'big'), SUITE_ICONS.traduci),
		drawn(r('Detta', 'Dictate', function () { actions['eurise-detta'](); }, 'big'), SUITE_ICONS.detta)]);
	SUITE.suite = true;
	var COMMENTO = r('Commento', 'Comment', function () { dispatch('insertcomment'); }, 'big');
	var IMMAGINE = r('Immagine', 'Image', function () { dispatch('localgraphic'); }, 'big');
	var TABELLA = r('Tabella', 'Table', function () { uno('.uno:InsertTable', { Columns: { type: 'long', value: 3 }, Rows: { type: 'long', value: 3 } }); }, 'big');
	var VISTA = g('Zoom', 'Zoom', [r('Ingrandisci', 'Zoom in', '.uno:ZoomPlus'), r('Riduci', 'Zoom out', '.uno:ZoomMinus'),
		r('Schermo intero', 'Full screen', '.uno:FullScreen')]);

	var SUITE_FOGLIO = g('Suite', 'Suite', SUITE.items.concat([
		lab(drawn(r('Viaggio', 'Trip', function () { actions['eurise-viaggio'](); }, 'big'), SUITE_ICONS.viaggio)),
		lab(drawn(r('Grafico', 'Chart', function () { actions['eurise-grafico'](); }, 'big'), SUITE_ICONS.grafico))]));
	SUITE_FOGLIO.suite = true;
	// Sul computer il gruppo Suite sta anche nella scheda Home, in fondo (tavola OffComputer).
	var SUITE_HOME = g('Suite', 'Suite', SUITE.items);
	SUITE_HOME.suite = true;
	SUITE_HOME.deskOnly = true;
	var CELLE_CARATTERE = g('Carattere', 'Font', [glyph(r('Grassetto', 'Bold', '.uno:Bold'), 'B'), glyph(r('Corsivo', 'Italic', '.uno:Italic'), 'I'),
		glyph(r('Sottolineato', 'Underline', '.uno:Underline'), 'U'), glyph(r('Barrato', 'Strikethrough', '.uno:Strikeout'), 'S'),
		colorItem('Colore del testo', 'Font color', 'font', '.uno:Color'), colorItem('Riempimento', 'Fill', 'back', '.uno:BackgroundColor')], '.uno:FontDialog');
	CELLE_CARATTERE.fonts = true;

	var RIBBONS = {
		text: [
			{ id: 'home', label: 'Home', groups: [APPUNTI, CARATTERE, PARAGRAFO,
				STILI,
				deskItems(g('Modifica', 'Editing', [r('Trova e sostituisci', 'Find & replace', '.uno:SearchDialog', 'big')]), [
					lab(ri('Trova', 'Find', function () { dispatch('showsearchbar'); }, false, '.uno:SearchDialog')),
					lab(r('Sostituisci', 'Replace', '.uno:SearchDialog')),
					lab(ri('Collegamento', 'Link', function () { dispatch('hyperlinkdialog'); }, false, '.uno:HyperlinkDialog'))]), SUITE_HOME] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), groups: [
				g('Tabelle e immagini', 'Tables and images', [TABELLA, IMMAGINE, r('Grafico', 'Chart', '.uno:InsertObjectChart', 'big'),
					r('Forma', 'Shape', '.uno:BasicShapes.rectangle', 'big')]),
				g('Testo', 'Text', [r('Collegamento', 'Link', function () { dispatch('hyperlinkdialog'); }, 'big'), COMMENTO,
					r('Nota a piè', 'Footnote', '.uno:InsertFootnote'), r('Simbolo', 'Symbol', '.uno:CharmapControl'),
					r('Data', 'Date', '.uno:InsertDateField'), r('Numero di pagina', 'Page number', '.uno:InsertPageNumberField')]),
				g('Pagine', 'Pages', [r('Interruzione di pagina', 'Page break', '.uno:InsertPagebreak', 'big'),
					r('Sommario', 'Table of contents', '.uno:InsertMultiIndex', 'big')])] },
			{ id: 'layout', label: 'Layout', groups: [
				g('Pagina', 'Page', [r('Imposta pagina', 'Page setup', '.uno:PageDialog', 'big'), r('Colonne', 'Columns', '.uno:FormatColumns', 'big'),
					r('Interruzione', 'Break', '.uno:InsertBreak', 'big')])] },
			{ id: 'revisione', label: t('Revisione', 'Review'), groups: [
				g('Correzione', 'Proofing', [r('Ortografia', 'Spelling', '.uno:SpellingAndGrammarDialog', 'big'), r('Conta parole', 'Word count', '.uno:WordCountDialog', 'big')]),
				g('Commenti', 'Comments', [COMMENTO]),
				g('Modifiche', 'Changes', [r('Traccia', 'Track', '.uno:TrackChanges', 'big'), r('Accetta', 'Accept', '.uno:AcceptTrackedChange'),
					r('Rifiuta', 'Reject', '.uno:RejectTrackedChange'), r('Gestisci', 'Manage', '.uno:AcceptTrackedChanges')])] },
			{ id: 'vista', label: t('Vista', 'View'), groups: [VISTA,
				g('Mostra', 'Show', [r('Righello', 'Ruler', '.uno:Ruler'), r('Segni di formattazione', 'Formatting marks', '.uno:ControlCodes'),
					r('Navigatore', 'Navigator', '.uno:Navigator')])] },
			{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE] }
		],
		spreadsheet: [
			{ id: 'celle', label: t('Celle', 'Cells'), groups: [APPUNTI,
				CELLE_CARATTERE,
				g('Allineamento', 'Alignment', [r('A sinistra', 'Left', '.uno:AlignLeft'), r('Al centro', 'Center', '.uno:AlignHorizontalCenter'),
					r('A destra', 'Right', '.uno:AlignRight'), r('Testo a capo', 'Wrap', '.uno:WrapText'),
					r('Unisci le celle', 'Merge', '.uno:ToggleMergeCells'), r('Bordi', 'Borders', '.uno:SetBorderStyle')], '.uno:FormatCellDialog'),
				g('Numeri', 'Numbers', [r('Valuta', 'Currency', '.uno:NumberFormatCurrency'), r('Percentuale', 'Percent', '.uno:NumberFormatPercent'),
					r('Migliaia', 'Thousands', '.uno:NumberFormatThousands'), r('Data', 'Date', '.uno:NumberFormatDate')], '.uno:FormatCellDialog'),
				g('Modifica', 'Editing', [r('Somma', 'Sum', '.uno:AutoSum', 'big'), r('Ordina', 'Sort', '.uno:SortAscending'),
					r('Filtra', 'Filter', '.uno:DataFilterAutoFilter')]),
				SUITE] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), groups: [
				g('Righe e colonne', 'Rows and columns', [r('Riga sopra', 'Row above', '.uno:InsertRowsBefore'), r('Riga sotto', 'Row below', '.uno:InsertRowsAfter'),
					r('Colonna prima', 'Column before', '.uno:InsertColumnsBefore'), r('Colonna dopo', 'Column after', '.uno:InsertColumnsAfter'),
					r('Elimina righe', 'Delete rows', '.uno:DeleteRows'), r('Elimina colonne', 'Delete columns', '.uno:DeleteColumns')]),
				g('Oggetti', 'Objects', [r('Grafico', 'Chart', '.uno:InsertObjectChart', 'big'), IMMAGINE, COMMENTO,
					r('Collegamento', 'Link', function () { dispatch('hyperlinkdialog'); }, 'big')]),
				g('Fogli', 'Sheets', [r('Nuovo foglio', 'New sheet', '.uno:Insert', 'big')])] },
			{ id: 'formule', label: t('Formule', 'Formulas'), groups: [
				g('Funzioni', 'Functions', [r('Somma', 'Sum', '.uno:AutoSum', 'big'), r('Funzione', 'Function', '.uno:FunctionDialog', 'big')]),
				g('Calcolo', 'Calculation', [r('Ricalcola', 'Recalculate', '.uno:Calculate', 'big'), r('Nomi', 'Names', '.uno:DefineName', 'big')])] },
			{ id: 'dati', label: t('Dati', 'Data'), groups: [
				g('Ordina e filtra', 'Sort and filter', [r('A-Z', 'A-Z', '.uno:SortAscending'), r('Z-A', 'Z-A', '.uno:SortDescending'),
					r('Ordina…', 'Sort…', '.uno:DataSort', 'big'), r('Filtro', 'Filter', '.uno:DataFilterAutoFilter', 'big'),
					r('Filtro standard', 'Standard filter', '.uno:DataFilterStandardFilter')]),
				g('Strumenti', 'Tools', [r('Convalida', 'Validity', '.uno:Validation', 'big'), r('Subtotali', 'Subtotals', '.uno:DataSubTotals', 'big'),
					r('Tabella pivot', 'Pivot table', '.uno:DataDataPilotRun', 'big')])] },
			{ id: 'revisione', label: t('Revisione', 'Review'), groups: [
				g('Revisione', 'Review', [COMMENTO, r('Ortografia', 'Spelling', '.uno:SpellDialog', 'big'),
					r('Traccia', 'Track', '.uno:TraceChangeMode', 'big'), r('Proteggi il foglio', 'Protect sheet', '.uno:Protect', 'big')])] },
			{ id: 'vista', label: t('Vista', 'View'), groups: [VISTA,
				g('Mostra', 'Show', [r('Blocca righe e colonne', 'Freeze', '.uno:FreezePanes', 'big'), r('Griglia', 'Grid', '.uno:ToggleSheetGrid'),
					r('Intestazioni', 'Headers', '.uno:ViewRowColumnHeaders')])] },
			{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE_FOGLIO] }
		],
		presentation: [
			{ id: 'home', label: 'Home', groups: [APPUNTI,
				DIAPOSITIVE, CARATTERE, PARAGRAFO, SUITE_HOME] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), groups: [
				g('Oggetti', 'Objects', [r('Casella di testo', 'Text box', '.uno:Text', 'big'), IMMAGINE, TABELLA,
					r('Forma', 'Shape', '.uno:BasicShapes.rectangle', 'big'), r('Grafico', 'Chart', '.uno:InsertObjectChart', 'big')]),
				g('Altro', 'More', [r('Collegamento', 'Link', function () { dispatch('hyperlinkdialog'); }, 'big'), COMMENTO])] },
			{ id: 'presenta', label: t('Presenta', 'Present'), groups: [
				g('Presentazione', 'Slideshow', [r('Dall\'inizio', 'From start', function () { dispatch('presentation'); }, 'big'),
					r('Dalla corrente', 'From current', function () { dispatch('presentation-currentslide'); }, 'big')])] },
			{ id: 'vista', label: t('Vista', 'View'), groups: [VISTA,
				g('Viste', 'Views', [r('Normale', 'Normal', '.uno:NormalMultiPaneGUI', 'big'), r('Note', 'Notes', '.uno:NotesMode', 'big'),
					r('Sequenza', 'Slide sorter', '.uno:DiaMode', 'big')])] },
			{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE] }
		]
	};
	RIBBONS.drawing = RIBBONS.presentation;

	var ribbon = { tab: null, el: null, buttons: [] };

	function commandIcon(command, size) {
		var img = document.createElement('img');
		img.alt = '';
		img.width = size;
		img.height = size;
		img.setAttribute('aria-hidden', 'true');
		if (typeof command === 'string') {
			// Le icone nostre si mettono direttamente: gli alias di Collabora cambiano il nome di alcuni comandi.
			var mine = euriseIcon(command.replace(/^\.uno:/, '').split('?')[0], darkTheme());
			if (mine) { img.src = mine; return img; }
		}
		if (typeof command === 'string' && app.LOUtil) {
			var name = app.LOUtil.getIconNameOfCommand(command);
			if (name) {
				try { app.LOUtil.setImage(img, name, app.map); } catch (e) { img.src = ''; }
				return img;
			}
		}
		return null;
	}

	/** Grande, piccola con il nome (solo sul computer) o piccola. */
	function kind(item) {
		if (onDesk() && item.desk) return item.desk;
		return item.big ? 'big' : 'small';
	}

	function ribbonButton(item, inSuite, tile) {
		var b = document.createElement('button');
		b.type = 'button';
		var k = kind(item);
		var big = k === 'big';
		b.className = 'eurise-rb' + (big ? ' big' : '') + (k === 'lab' ? ' lab' : '') + (inSuite ? ' suite' : '');
		b.title = item.label;
		b.setAttribute('aria-label', item.label);
		if (tile) {
			// Tessera di stile (tavola OffComputer): «Aa» nell'aspetto dello stile e il nome sotto.
			b.className = 'eurise-rb tile ' + tile;
			b.innerHTML = '<span class="aa">Aa</span>';
			var name = document.createElement('span');
			name.textContent = item.label;
			b.appendChild(name);
			b.onclick = function () { item.run(); };
			return b;
		}
		var size = big ? (onDesk() ? 24 : 26) : 18;
		var icon = item.svg && onDesk() ? null : commandIcon(item.icon || (typeof item.run === 'string' ? item.run.split('?')[0] : item.run), size);
		if (item.svg && onDesk()) {
			b.insertAdjacentHTML('beforeend', '<svg width="' + size + '" height="' + size + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
				'stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + item.svg + '"></path></svg>');
			icon = b.lastChild;
		}
		if (icon && !icon.parentNode) b.appendChild(icon);
		if (item.swatch) { b.classList.add('swatched'); b.appendChild(swatch(item.swatch)); }
		if (big || k === 'lab' || !icon) {
			var span = document.createElement('span');
			span.textContent = (onDesk() && item.deskLabel) || item.label;
			b.appendChild(span);
		}
		if (typeof item.run === 'string') {
			b.setAttribute('data-uno', item.run);
			if (phone.states[item.run] === 'true') b.classList.add('on');
		}
		if (item.menu) {
			b.classList.add('has-menu');
			b.insertAdjacentHTML('beforeend', '<svg class="eurise-caret" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"></path></svg>');
		}
		b.onclick = function () {
			anchorEl = b;
			if (onDesk()) { countUse(item); peekDone(); }
			if (typeof item.run === 'string') uno(item.run); else item.run();
		};
		return b;
	}

	function showRibbon(tab) {
		ribbon.tab = tab.id;
		Array.prototype.forEach.call(document.querySelectorAll('#eurise-tabs .eurise-rtab'), function (el) {
			el.classList.toggle('active', el.getAttribute('data-id') === tab.id);
		});
		var el = ribbon.el;
		el.innerHTML = '';
		tab.groups.forEach(function (group) {
			if (group.deskOnly && !onDesk()) return;
			var items = onDesk() && group.yours ? yourItems(group) : onDesk() && group.deskItems ? group.deskItems : group.items;
			var sec = document.createElement('section');
			sec.className = 'eurise-group' + (group.suite ? ' suite' : '');
			sec.setAttribute('aria-label', group.label);
			var body = document.createElement('div');
			body.className = 'eurise-gbody';
			if (group.fonts) {
				// Carattere: tendine sopra, una fila di pulsanti sotto (tavola OffTablet).
				var column = document.createElement('div');
				column.className = 'eurise-fontcol';
				column.appendChild(fontRow(false, onDesk() ? group.fontExtras : null));
				var line = document.createElement('div');
				line.className = 'eurise-fontline';
				items.forEach(function (item) { line.appendChild(ribbonButton(item, group.suite)); });
				column.appendChild(line);
				body.appendChild(column);
			} else if (group.rows && onDesk()) {
				var stack = document.createElement('div');
				stack.className = 'eurise-fontcol eurise-rows';
				group.rows.forEach(function (list) {
					var line = document.createElement('div');
					line.className = 'eurise-fontline';
					list.forEach(function (item) { line.appendChild(ribbonButton(item, group.suite)); });
					stack.appendChild(line);
				});
				body.appendChild(stack);
			} else if (group.tiles && onDesk()) {
				items.forEach(function (item, i) { body.appendChild(ribbonButton(item, false, 'st' + i)); });
			} else {
				var smalls = null;
				// Sul computer le voci grandi vengono prima (Incolla, Detta, Traduci), poi le piccole in colonna.
				var ordered = onDesk() ? items.filter(function (it) { return kind(it) === 'big'; })
					.concat(items.filter(function (it) { return kind(it) !== 'big'; })) : items;
				ordered.forEach(function (item) {
					var k = kind(item);
					if (k === 'big') { smalls = null; body.appendChild(ribbonButton(item, group.suite)); return; }
					// Le voci piccole stanno in griglia su due righe, come nella tavola.
					var cls = 'eurise-smalls' + (k === 'lab' ? ' labs' : '');
					if (!smalls || smalls.className !== cls) { smalls = document.createElement('div'); smalls.className = cls; body.appendChild(smalls); }
					smalls.appendChild(ribbonButton(item, group.suite));
				});
			}
			sec.appendChild(body);
			sec.appendChild(groupFoot(group));
			el.appendChild(sec);
		});
	}

	/** Etichetta del gruppo in basso, con «altre opzioni» se il gruppo ha un dialogo completo. */
	function groupFoot(group) {
		var foot = document.createElement('div');
		foot.className = 'eurise-gfoot';
		if (group.suite) foot.innerHTML = '<svg width="10" height="10" viewBox="0 0 24 24" aria-hidden="true"><path fill="#B8923F" d="' + SVG.star + '"></path></svg>';
		var label = document.createElement('span');
		label.textContent = group.label;
		foot.appendChild(label);
		var moreCommand = (onDesk() && group.deskMore) || group.more;
		if (moreCommand) {
			var more = document.createElement('button');
			more.type = 'button';
			more.className = 'eurise-gmore';
			more.title = t('Altre opzioni di ', 'More options: ') + group.label;
			more.setAttribute('aria-label', more.title);
			more.innerHTML = '<svg width="11" height="11" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M3 9L9 3 M5 3h4v4"></path></svg>';
			more.onclick = function () { uno(moreCommand); };
			foot.appendChild(more);
		}
		return foot;
	}

	// --- Telefono: le schede della dock prendono i gruppi del nastro (tavola OffTelefonoGruppi) ---------------------

	function tabGroups(docType, id) {
		var tabs = RIBBONS[docType] || RIBBONS.text;
		for (var i = 0; i < tabs.length; i++) if (tabs[i].id === id) return tabs[i].groups;
		return [];
	}

	var DOCUMENTO = g('Documento', 'Document', [
		ri('Trova', 'Find', function () { dispatch('showsearchbar'); }, 'big', '.uno:SearchDialog'),
		ri('PDF', 'PDF', function () { beforeSharing(); }, 'big', '.uno:ExportToPDF'),
		ri('Stampa', 'Print', function () { dispatch('print'); }, 'big', '.uno:Print'),
		ri('Salva', 'Save', function () { saveNow(false); }, 'big', '.uno:Save'),
		r('Tutte le opzioni', 'All options', function () { dispatch('mobile_wizard'); }, 'big')]);

	/** Schede della dock del telefono: ciascuna con i suoi gruppi del nastro, nell'ordine della tavola. */
	function phoneTabs(docType) {
		var tab = function (id, label, groups, extra) {
			var out = { id: id, label: label, groups: groups };
			if (extra) out[extra] = true;
			return out;
		};
		if (docType === 'spreadsheet') {
			return [
				// Come sul testo: prima il Carattere, gli Appunti in fondo.
				tab('celle', t('Celle', 'Cells'), tabGroups('spreadsheet', 'celle').filter(function (gr) { return !gr.suite && gr !== APPUNTI; }).concat([APPUNTI])),
				tab('inserisci', t('Inserisci', 'Insert'), tabGroups('spreadsheet', 'inserisci')),
				tab('dati', t('Dati', 'Data'), tabGroups('spreadsheet', 'formule').concat(tabGroups('spreadsheet', 'dati'))),
				tab('suite', 'Suite', tabGroups('spreadsheet', 'suite'), 'suite'),
				tab('altro', t('Altro', 'More'), tabGroups('spreadsheet', 'revisione').concat(tabGroups('spreadsheet', 'vista'), [DOCUMENTO]), 'more')
			];
		}
		if (docType === 'presentation' || docType === 'drawing') {
			return [
				tab('diapositiva', t('Diapositiva', 'Slide'), [DIAPOSITIVE].concat(tabGroups('presentation', 'presenta'))),
				tab('testo', t('Testo', 'Text'), [CARATTERE, PARAGRAFO, APPUNTI]),
				tab('inserisci', t('Inserisci', 'Insert'), tabGroups('presentation', 'inserisci')),
				tab('suite', 'Suite', tabGroups('presentation', 'suite'), 'suite'),
				tab('altro', t('Altro', 'More'), tabGroups('presentation', 'vista').concat([DOCUMENTO]), 'more')
			];
		}
		return [
			tab('testo', t('Testo', 'Text'), [CARATTERE, PARAGRAFO, STILI, APPUNTI]),
			tab('inserisci', t('Inserisci', 'Insert'), tabGroups('text', 'inserisci')),
			tab('revisione', t('Revisione', 'Review'), tabGroups('text', 'revisione')),
			tab('suite', 'Suite', tabGroups('text', 'suite'), 'suite'),
			tab('altro', t('Altro', 'More'), tabGroups('text', 'layout').concat(tabGroups('text', 'vista'), [DOCUMENTO]), 'more')
		];
	}

	/**
	 * Computer: comandi del guscio Qt di Collabora (Bridge.cpp) con le finestre del sistema. «Apri da Eurise File» apre la
	 * scelta dei file finché Eurise File non c'è anche sul computer; ogni documento si apre nella sua finestra.
	 */
	function shellCommand(command) {
		if (window.postMobileMessage) window.postMobileMessage('uno ' + command);
		else uno(command);
	}

	function fileMenu() {
		if (onDesk()) { deskFileMenu(); return; }
		chooser(t('File', 'File'), (onDesk() ? [
			[t('Apri da Eurise File…', 'Open from Eurise File…'), function () { shellCommand('.uno:Open'); }],
			[t('Nuovo documento', 'New document'), function () { shellCommand('.uno:NewDocText'); }],
			[t('Nuovo foglio', 'New spreadsheet'), function () { shellCommand('.uno:NewDocSpreadsheet'); }],
			[t('Nuova presentazione', 'New presentation'), function () { shellCommand('.uno:NewDocPresentation'); }],
			[t('Salva con nome…', 'Save as…'), function () { shellCommand('.uno:SaveAs'); }]
		] : []).concat([
			[t('Salva', 'Save'), function () { saveNow(false); }],
			[t('Condividi o esporta in PDF', 'Share or export to PDF'), beforeSharing],
			[t('Stampa', 'Print'), function () { dispatch('print'); }],
			[t('Proprietà del documento', 'Document properties'), function () { uno('.uno:SetDocumentProperties'); }]].concat(onDesk() ? [
			[t('Colore delle icone', 'Icon colour'), chooseAccent]] : []).concat([
			[t('Chiudi', 'Close'), function () { if (window.ThisIsAMobileApp) window.postMobileMessage('BYE'); else dispatch('closemobile'); }]
		])), function (run) { run(); });
	}

	/** Computer: scelta dell'accento delle icone tra le tinte della suite; resta nella memoria del guscio. */
	function chooseAccent() {
		var box = overlay('eurise-colors');
		var h = document.createElement('h2');
		h.textContent = t('Colore delle icone', 'Icon colour');
		box.appendChild(h);
		var grid = document.createElement('div');
		grid.className = 'eurise-swatches';
		ACCENTS.forEach(function (c) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-sw' + (c[2] === ACCENT.light ? ' on' : '');
			b.innerHTML = '<span></span>';
			b.firstChild.style.background = darkTheme() ? c[3] : c[2];
			b.appendChild(document.createTextNode(t(c[0], c[1])));
			b.onclick = function () {
				closeOverlay();
				setAccent(c[2], c[3]);
				try { window.localStorage.setItem('eurise-accento', JSON.stringify({ light: c[2], dark: c[3] })); } catch (e) { /* niente memoria */ }
			};
			grid.appendChild(b);
		});
		box.appendChild(grid);
	}

	// =================================================================================================================
	// Computer (guscio Linux): tavole OffPCInizio, OffPCNastro, OffPCTendine, OffPCFile, OffPCFinestre, approvate dal
	// proprietario il 15 novembre dopo la prima prova del Flatpak («via direttamente»).
	// =================================================================================================================

	// --- Tendine piccole attaccate al comando (sul computer; i fogli grandi al centro restano per il tablet) ---------

	/** Ultimo pulsante premuto: è il comando a cui si attacca la tendina che apre. */
	var anchorEl = null;
	document.addEventListener('mousedown', function (e) {
		var b = e.target && e.target.closest && e.target.closest('button');
		if (b && !b.closest('#eurise-pop, .eurise-sub')) anchorEl = b;
	}, true);

	function closePop() {
		Array.prototype.forEach.call(document.querySelectorAll('#eurise-pop, .eurise-sub'), function (el) { el.remove(); });
		document.removeEventListener('mousedown', popOutside, true);
		document.removeEventListener('keydown', popKeys, true);
		if (app.map && app.map.focus) app.map.focus();
	}

	function popOutside(e) {
		if (e.target.closest && e.target.closest('#eurise-pop, .eurise-sub')) return;
		closePop();
	}

	/** Frecce su e giù tra le voci, Invio le sceglie, Esc chiude, freccia a destra apre il sottomenu. */
	function popKeys(e) {
		var pop = document.querySelector('.eurise-sub') || document.getElementById('eurise-pop');
		if (!pop) return;
		if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); closePop(); return; }
		var items = Array.prototype.slice.call(pop.querySelectorAll('.eurise-pi'));
		if (!items.length) return;
		var i = items.indexOf(document.activeElement);
		if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			e.stopPropagation();
			i = e.key === 'ArrowDown' ? (i + 1) % items.length : (i <= 0 ? items.length - 1 : i - 1);
			items[i].focus();
		} else if ((e.key === 'Enter' || e.key === 'ArrowRight') && i >= 0) {
			if (e.key === 'ArrowRight' && !items[i].classList.contains('has-sub')) return;
			e.preventDefault();
			e.stopPropagation();
			items[i].click();
		} else if (e.key === 'ArrowLeft' && pop.classList.contains('eurise-sub')) {
			e.preventDefault();
			pop.remove();
		}
	}

	/** Tendina sotto il comando (o accanto, per i sottomenu); resta dentro la finestra. */
	function popover(anchor, cls, sub) {
		if (!sub) closePop();
		else Array.prototype.forEach.call(document.querySelectorAll('.eurise-sub'), function (el) { el.remove(); });
		var pop = document.createElement('div');
		if (sub) pop.className = 'eurise-sub eurise-pop ' + (cls || '');
		else { pop.id = 'eurise-pop'; pop.className = 'eurise-pop ' + (cls || ''); }
		pop.setAttribute('role', 'menu');
		document.body.appendChild(pop);
		var place = function () {
			var r = anchor && anchor.getBoundingClientRect ? anchor.getBoundingClientRect() : { left: 12, bottom: 84, right: 12, top: 84 };
			var w = pop.offsetWidth, h = pop.offsetHeight;
			var x = sub ? r.right + 4 : r.left, y = sub ? r.top - 6 : r.bottom + 4;
			if (x + w > window.innerWidth - 8) x = sub ? r.left - w - 4 : window.innerWidth - w - 8;
			if (y + h > window.innerHeight - 8) y = Math.max(8, window.innerHeight - h - 8);
			pop.style.left = Math.max(8, x) + 'px';
			pop.style.top = y + 'px';
		};
		pop.place = place;
		setTimeout(place, 0);
		if (!sub) {
			setTimeout(function () {
				document.addEventListener('mousedown', popOutside, true);
				document.addEventListener('keydown', popKeys, true);
			}, 0);
		}
		return pop;
	}

	function popSection(pop, text) {
		var s = document.createElement('div');
		s.className = 'eurise-ps';
		s.textContent = text;
		pop.appendChild(s);
		return s;
	}

	/** Voce della tendina: nome, icona facoltativa, scorciatoia scritta accanto, segno di spunta o sottomenu. */
	function popItem(pop, label, run, opts) {
		opts = opts || {};
		var b = document.createElement('button');
		b.type = 'button';
		b.className = 'eurise-pi' + (opts.on ? ' on' : '') + (opts.sub ? ' has-sub' : '');
		b.setAttribute('role', 'menuitem');
		if (opts.svg) b.insertAdjacentHTML('beforeend', icon(opts.svg, 17));
		else if (opts.check !== undefined) b.insertAdjacentHTML('beforeend', '<span class="eurise-pck">' + (opts.check ? icon('M5 12l5 5 9-10', 14) : '') + '</span>');
		var name = document.createElement('span');
		name.className = 'eurise-pn';
		name.textContent = label;
		if (opts.font) name.style.fontFamily = '"' + opts.font + '", ' + (/serif|georgia|times|caladea|liberation serif|dejavu serif/i.test(opts.font) ? 'serif' : 'sans-serif');
		b.appendChild(name);
		if (opts.key) {
			var k = document.createElement('kbd');
			k.textContent = opts.key;
			b.appendChild(k);
		}
		if (opts.sub) b.insertAdjacentHTML('beforeend', icon('M9 6l6 6-6 6', 13));
		b.onclick = function (e) {
			e.stopPropagation();
			if (opts.sub) { opts.sub(b); return; }
			closePop();
			if (typeof run === 'string') uno(run); else if (run) run();
		};
		if (opts.sub) b.onmouseenter = function () { opts.sub(b); };
		else b.onmouseenter = function () { if (!b.closest('.eurise-sub')) Array.prototype.forEach.call(document.querySelectorAll('.eurise-sub'), function (el) { el.remove(); }); };
		pop.appendChild(b);
		return b;
	}

	/** Tendina di un comando del nastro con più scelte (Margini, Orientamento, Forme, A capo…). */
	function menuOf(entries) {
		return function () {
			var pop = popover(anchorEl, 'menu');
			(typeof entries === 'function' ? entries() : entries).forEach(function (e) {
				if (typeof e === 'string') popSection(pop, e);
				else popItem(pop, e[0], e[1], { svg: e[2] });
			});
			var first = pop.querySelector('.eurise-pi');
			if (first) first.focus();
		};
	}

	// Carattere: in cima quelli del documento e gli ultimi usati, poi tutti, ciascuno nel suo stile.
	function recentFonts() {
		try { return JSON.parse(window.localStorage.getItem('eurise-caratteri') || '[]'); } catch (e) { return []; }
	}
	function useFont(name) {
		var list = recentFonts().filter(function (n) { return n !== name; });
		list.unshift(name);
		try { window.localStorage.setItem('eurise-caratteri', JSON.stringify(list.slice(0, 5))); } catch (e) { /* niente memoria */ }
		app.map.applyFont(name);
	}

	function deskFonts() {
		var pop = popover(anchorEl, 'fonts');
		var field = document.createElement('input');
		field.type = 'search';
		field.placeholder = t('Scrivi per cercare', 'Type to search');
		field.setAttribute('aria-label', t('Cerca un carattere', 'Search a font'));
		pop.appendChild(field);
		var list = document.createElement('div');
		list.className = 'eurise-plist';
		pop.appendChild(list);
		var current = phone.states['.uno:CharFontName'] || '';
		function render() {
			var names = fontNames();
			var q = fold(field.value.trim());
			list.innerHTML = '';
			if (!names.length) {
				var wait = document.createElement('p');
				wait.className = 'eurise-wait';
				wait.textContent = t('Carico i caratteri…', 'Loading fonts…');
				list.appendChild(wait);
				return;
			}
			var add = function (n) { popItem(list, n, function () { useFont(n); }, { font: n, check: n === current }); };
			if (q) {
				names.filter(function (n) { return fold(n).indexOf(q) >= 0; }).slice(0, 200).forEach(add);
			} else {
				if (current) { popSection(list, t('Nel documento', 'In the document')); add(current); }
				var recent = recentFonts().filter(function (n) { return n !== current && names.indexOf(n) >= 0; });
				if (recent.length) { popSection(list, t('Usati di recente', 'Recently used')); recent.forEach(add); }
				popSection(list, t('Tutti', 'All'));
				names.slice(0, 400).forEach(add);
			}
			pop.place();
		}
		field.oninput = render;
		field.onkeydown = function (e) {
			if (e.key === 'Enter') {
				var first = list.querySelector('.eurise-pi');
				if (first) { e.preventDefault(); first.click(); }
			} else if (e.key === 'ArrowDown') {
				var f = list.querySelector('.eurise-pi');
				if (f) { e.preventDefault(); f.focus(); }
			}
		};
		render();
		setTimeout(function () { field.focus(); }, 0);
		if (!fontNames().length) {
			app.map.once('updatetoolbarcommandvalues', render);
			if (app.socket && app.socket.sendMessage) app.socket.sendMessage('commandvalues command=.uno:CharFontName');
		}
	}

	var DESK_SIZES = ['8', '9', '10', '10.5', '11', '12', '14', '16', '18', '20', '24', '28', '36', '48', '72'];

	function deskSizes() {
		var pop = popover(anchorEl, 'sizes');
		var field = document.createElement('input');
		field.type = 'text';
		field.inputMode = 'decimal';
		field.placeholder = shownState('.uno:FontHeight');
		field.setAttribute('aria-label', t('Dimensione in punti', 'Size in points'));
		field.onkeydown = function (e) {
			if (e.key !== 'Enter') return;
			var v = parseFloat(String(field.value).replace(',', '.'));
			if (v > 0 && v < 1000) { closePop(); app.map.applyFontSize(String(v)); }
		};
		pop.appendChild(field);
		var current = shownState('.uno:FontHeight');
		DESK_SIZES.forEach(function (s) {
			popItem(pop, s, function () { app.map.applyFontSize(s); }, { on: s === current });
		});
		setTimeout(function () { field.focus(); }, 0);
	}

	function deskColors(kind) {
		var pop = popover(anchorEl, 'colors');
		popSection(pop, kind === 'back' ? (app.map.getDocType() === 'spreadsheet' ? t('Riempimento', 'Fill') : t('Evidenzia', 'Highlight')) : t('Colore del carattere', 'Font color'));
		var grid = document.createElement('div');
		grid.className = 'eurise-pgrid';
		var apply = function (value) {
			var command = colorCommand(kind);
			var params = {};
			params[command.replace('.uno:', '') + '.Color'] = { type: 'long', value: value };
			uno(command, params);
			if (value >= 0) {
				lastColor[kind] = value;
				Array.prototype.forEach.call(document.querySelectorAll('[data-swatch="' + kind + '"]'), function (el) { el.style.background = hex(value); });
			}
		};
		var all = COLORS[kind].filter(function (c) { return c[1] >= 0; }).concat(kind === 'font' ?
			[[t('Bianco', 'White'), 0xFFFFFF], [t('Salvia', 'Sage'), 0x4F6B5A], [t('Prugna', 'Plum'), 0x6B4A7A]] :
			[[t('Giallo', 'Yellow'), 0xFFE36E], [t('Verde chiaro', 'Light green'), 0xB8E3A8], [t('Azzurro', 'Light blue'), 0xA9D4F2]]);
		all.forEach(function (c) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-pc' + (c[1] === lastColor[kind] ? ' on' : '');
			b.title = c[0];
			b.setAttribute('aria-label', c[0]);
			b.style.background = hex(c[1]);
			b.onclick = function () { closePop(); apply(c[1]); };
			grid.appendChild(b);
		});
		pop.appendChild(grid);
		popItem(pop, kind === 'back' ? t('Nessuno', 'None') : t('Automatico', 'Automatic'), function () { apply(-1); }, { svg: 'M6 6l12 12 M18 6L6 18' });
		popItem(pop, t('Altri colori…', 'More colours…'), kind === 'font' || app.map.getDocType() !== 'spreadsheet' ? '.uno:FontDialog' : '.uno:FormatCellDialog');
	}

	// --- Menu File del computer: compatto, con le scorciatoie (tavola OffPCFile) ---------------------------------------

	var SVGD = {
		doc: 'M6 3h8l4 4v14H6z M14 3v4h4 M9 12h6 M9 16h6',
		sheet: 'M4 4h16v16H4z M4 9h16 M4 14h16 M10 4v16',
		slide: 'M3 5h18v12H3z M12 17v3 M8 20h8',
		folder: 'M3 6h6l2 2h10v11H3z',
		clock: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M12 7v5l3 2',
		pc: 'M3 4h18v12H3z M8 20h8 M12 16v4',
		save: 'M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6',
		pencil: 'M4 20h4L19 9l-4-4L4 16z M14 6l4 4',
		export: 'M12 3v12 M7 8l5-5 5 5 M5 14v6h14v-6',
		print: 'M7 8V3h10v5 M5 8h14v8h-3 M8 16H5v-8 M8 13h8v8H8z',
		info: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M12 11v6 M12 7.5v.01',
		lock: 'M6 11h12v9H6z M9 11V8a3 3 0 0 1 6 0v3',
		color: 'M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-2 0-3 4 0 5-2c1.5-3-2.5-11-7-11z',
		close: 'M6 6l12 12 M18 6L6 18'
	};

	function recentDocs(done) {
		if (!window.postMobileCall) { done([]); return; }
		Promise.resolve(window.postMobileCall('GETRECENTDOCS')).then(function (res) {
			var list = [];
			try { list = typeof res === 'string' ? JSON.parse(res) : (res || []); } catch (e) { list = []; }
			done(Array.isArray(list) ? list : []);
		}, function () { done([]); });
	}

	function docKindOf(name, doctype) {
		if (doctype === 'calc' || /\.(xlsx?|ods|csv)$/i.test(name)) return 'sheet';
		if (doctype === 'impress' || /\.(pptx?|odp)$/i.test(name)) return 'slide';
		return 'doc';
	}

	function recentInfo(doc) {
		var uri = doc.displayUri || doc.uri || '';
		var path = uri;
		try { path = decodeURIComponent(new URL(uri).pathname); } catch (e) { /* già un percorso */ }
		var parts = path.split('/').filter(Boolean);
		var stamp = doc.timestamp;
		if (/^\d+$/.test(String(stamp || ''))) { stamp = Number(stamp); if (stamp < 1e12) stamp *= 1000; }
		return { uri: doc.uri || uri, name: parts.pop() || uri, folder: parts.slice(-2).join(' › '), kind: docKindOf(path, doc.doctype), when: stamp };
	}

	function openRecent(uri) {
		if (window.postMobileMessage) window.postMobileMessage('opendoc file=' + encodeURIComponent(uri));
	}

	function deskFileMenu() {
		var pop = popover(anchorEl, 'file');
		popSection(pop, t('Nuovo', 'New'));
		var row = document.createElement('div');
		row.className = 'eurise-pnew';
		[[t('Documento', 'Document'), '.uno:NewDocText', SVGD.doc, 'doc'], [t('Foglio', 'Spreadsheet'), '.uno:NewDocSpreadsheet', SVGD.sheet, 'sheet'],
			[t('Presentazione', 'Presentation'), '.uno:NewDocPresentation', SVGD.slide, 'slide']].forEach(function (n) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'eurise-pnb ' + n[3];
			b.innerHTML = icon(n[2], 20) + '<span></span>';
			b.lastChild.textContent = n[0];
			b.onclick = function () { closePop(); shellCommand(n[1]); };
			row.appendChild(b);
		});
		pop.appendChild(row);
		popItem(pop, t('Apri da Eurise File…', 'Open from Eurise File…'), function () { shellCommand('.uno:Open'); }, { svg: SVGD.folder, key: 'Ctrl O' });
		popItem(pop, t('Recenti', 'Recent'), null, { svg: SVGD.clock, sub: function (b) {
			recentDocs(function (docs) {
				var sub = popover(b, 'recent', true);
				if (!docs.length) popSection(sub, t('Nessun documento recente', 'No recent documents'));
				docs.slice(0, 10).forEach(function (d) {
					var info = recentInfo(d);
					popItem(sub, info.name, function () { openRecent(info.uri); }, { svg: SVGD[info.kind] });
				});
				sub.place();
			});
		} });
		popSection(pop, t('Questo documento', 'This document'));
		popItem(pop, t('Salva', 'Save'), function () { saveNow(false); }, { svg: SVGD.save, key: 'Ctrl S' });
		popItem(pop, t('Salva con nome…', 'Save as…'), function () { shellCommand('.uno:SaveAs'); }, { svg: SVGD.save, key: 'Ctrl Maiusc S' });
		popItem(pop, t('Rinomina', 'Rename'), startRename, { svg: SVGD.pencil, key: 'F2' });
		popItem(pop, t('Esporta', 'Export'), null, { svg: SVGD.export, sub: function (b) {
			var sub = popover(b, 'export', true);
			var type = app.map.getDocType();
			var formats = isPdf() ? [] : type === 'spreadsheet' ? [['Excel', 'xlsx'], ['OpenDocument', 'ods'], ['CSV', 'csv']] :
				type === 'presentation' || type === 'drawing' ? [['PowerPoint', 'pptx'], ['OpenDocument', 'odp']] :
				[['Word', 'docx'], ['OpenDocument', 'odt'], [t('Testo RTF', 'Rich text'), 'rtf'], ['EPUB', 'epub'], [t('Pagina web', 'Web page'), 'html']];
			popItem(sub, 'PDF', function () { beforeSharing(); }, { key: '.pdf' });
			formats.forEach(function (f) { popItem(sub, f[0], function () { dispatch('downloadas-' + f[1]); }, { key: '.' + f[1] }); });
			sub.place();
		} });
		popItem(pop, t('Stampa…', 'Print…'), function () { dispatch('print'); }, { svg: SVGD.print, key: 'Ctrl P' });
		popItem(pop, t('Proprietà', 'Properties'), '.uno:SetDocumentProperties', { svg: SVGD.info });
		popSection(pop, 'Eurise');
		popItem(pop, t('Colore delle icone', 'Icon colour'), null, { svg: SVGD.color, sub: function (b) {
			var sub = popover(b, 'accents', true);
			ACCENTS.forEach(function (c) {
				var item = popItem(sub, t(c[0], c[1]), function () {
					setAccent(c[2], c[3]);
					try { window.localStorage.setItem('eurise-accento', JSON.stringify({ light: c[2], dark: c[3] })); } catch (e) { /* niente memoria */ }
				}, { check: c[2] === ACCENT.light });
				var dot = document.createElement('i');
				dot.className = 'eurise-pdot';
				dot.style.background = darkTheme() ? c[3] : c[2];
				item.insertBefore(dot, item.querySelector('.eurise-pn'));
			});
			sub.place();
		} });
		popItem(pop, t('Chiudi documento', 'Close document'), function () { shellCommand('.uno:CloseWin'); }, { svg: SVGD.close, key: 'Ctrl W' });
		var first = pop.querySelector('.eurise-pi');
		if (first) first.focus();
	}

	// --- Trova e sostituisci del computer: una barra in alto sul documento (tavola OffPCFinestre) -----------------------

	/** Comandi di SearchItem: 0 trova, 1 trova tutto, 2 sostituisci, 3 sostituisci tutto. */
	function searchDoc(text, backward, replace, command) {
		if (!text || !app.searchService) return;
		app.searchService.search(text, !!backward, replace || '', command || 0);
	}

	function deskFind(withReplace) {
		var bar = document.getElementById('eurise-find');
		if (!bar) {
			bar = document.createElement('div');
			bar.id = 'eurise-find';
			bar.setAttribute('role', 'search');
			var btn = function (a, label, path) {
				return '<button type="button" class="eurise-fb" data-a="' + a + '" title="' + label + '" aria-label="' + label + '">' + icon(path, 17) + '</button>';
			};
			bar.innerHTML =
				'<div class="ef-row"><label class="ef-field">' + icon(SVG.search, 15) + '<input type="search" class="ef-find"></label>' +
				btn('prev', t('Precedente', 'Previous') + ' (Maiusc Invio)', 'M6 15l6-6 6 6') + btn('next', t('Successivo', 'Next') + ' (Invio)', 'M6 9l6 6 6-6') +
				btn('all', t('Evidenzia tutti', 'Highlight all'), 'M4 6h16 M4 12h16 M4 18h10') + btn('close', t('Chiudi', 'Close') + ' (Esc)', 'M6 6l12 12 M18 6L6 18') + '</div>' +
				'<div class="ef-row ef-replace"><label class="ef-field">' + icon('M4 7h11 M12 4l3 3-3 3 M20 17H9 M12 14l-3 3 3 3', 15) + '<input type="text" class="ef-with"></label>' +
				'<button type="button" class="ef-text" data-a="replace"></button><button type="button" class="ef-text dark" data-a="replaceall"></button></div>' +
				'<div class="ef-foot"><button type="button" class="ef-link" data-a="toggle"></button><span class="es-fill"></span><button type="button" class="ef-link" data-a="dialog"></button></div>';
			var find = bar.querySelector('.ef-find');
			var withEl = bar.querySelector('.ef-with');
			find.placeholder = t('Trova nel documento', 'Find in document');
			find.setAttribute('aria-label', find.placeholder);
			withEl.placeholder = t('Sostituisci con', 'Replace with');
			withEl.setAttribute('aria-label', withEl.placeholder);
			bar.querySelector('[data-a="replace"]').textContent = t('Sostituisci', 'Replace');
			bar.querySelector('[data-a="replaceall"]').textContent = t('Tutti', 'All');
			bar.querySelector('[data-a="dialog"]').textContent = t('Altre opzioni', 'More options');
			var close = function () {
				bar.remove();
				if (app.searchService && app.searchService.resetSelection) app.searchService.resetSelection();
				if (app.map && app.map.focus) app.map.focus();
			};
			var setReplace = function (on) {
				bar.classList.toggle('with-replace', on);
				bar.querySelector('[data-a="toggle"]').textContent = on ? t('Solo trova', 'Find only') : t('Sostituisci…', 'Replace…');
			};
			bar.setReplace = setReplace;
			bar.onclick = function (e) {
				var b = e.target.closest('button');
				if (!b) return;
				var a = b.getAttribute('data-a');
				if (a === 'next') searchDoc(find.value, false);
				else if (a === 'prev') searchDoc(find.value, true);
				else if (a === 'all') searchDoc(find.value, false, '', 1);
				else if (a === 'replace') searchDoc(find.value, false, withEl.value, 2);
				else if (a === 'replaceall') searchDoc(find.value, false, withEl.value, 3);
				else if (a === 'toggle') setReplace(!bar.classList.contains('with-replace'));
				else if (a === 'dialog') { close(); uno('.uno:SearchDialog'); }
				else if (a === 'close') close();
			};
			bar.onkeydown = function (e) {
				e.stopPropagation();
				if (e.key === 'Escape') { e.preventDefault(); close(); }
				else if (e.key === 'Enter' && e.target === find) { e.preventDefault(); searchDoc(find.value, e.shiftKey); }
				else if (e.key === 'Enter' && e.target === withEl) { e.preventDefault(); searchDoc(find.value, false, withEl.value, 2); }
			};
			var host = document.getElementById('document-container') || document.body;
			host.appendChild(bar);
		}
		bar.setReplace(!!withReplace);
		var input = bar.querySelector('.ef-find');
		var picked = '';
		try { picked = String(window.getSelection ? window.getSelection() : '').trim(); } catch (e) { picked = ''; }
		if (picked && picked.length < 80 && !input.value) input.value = picked;
		input.focus();
		input.select();
	}

	// --- Titolo nella pillola: un clic e si cambia il nome (tavola OffPCTendine) ---------------------------------------

	/**
	 * Rinomina sul posto: «saveas» nella stessa cartella con il nome nuovo (il guscio poi riapre lì il documento, come
	 * dopo Salva con nome) e il messaggio EURISERINOMINA, con cui il guscio Eurise toglie il file col nome vecchio
	 * (patch 02 della strada 2). L'estensione resta quella del file.
	 */
	function startRename() {
		var nameEl = document.querySelector('#eurise-header .eurise-name');
		var path = documentPath();
		if (!nameEl || !path || nameEl.querySelector('input')) return;
		var full = path.split('/').pop();
		var dot = full.lastIndexOf('.');
		var base = dot > 0 ? full.slice(0, dot) : full;
		var ext = dot > 0 ? full.slice(dot) : '';
		nameEl.innerHTML = '';
		nameEl.classList.add('editing');
		var input = document.createElement('input');
		input.type = 'text';
		input.value = base;
		input.setAttribute('aria-label', t('Nome del documento', 'Document name'));
		input.size = Math.max(6, base.length + 1);
		var tail = document.createElement('span');
		tail.className = 'eurise-ext';
		tail.textContent = ext;
		nameEl.appendChild(input);
		nameEl.appendChild(tail);
		var done = false;
		var finish = function (apply) {
			if (done) return;
			done = true;
			var wanted = input.value.trim().replace(/[\/\\:*?"<>|]/g, '');
			nameEl.classList.remove('editing');
			nameEl.textContent = (apply && wanted ? wanted : base) + ext;
			if (!apply || !wanted || wanted === base) return;
			var target = path.slice(0, path.length - full.length) + wanted + ext;
			if (window.postMobileMessage) window.postMobileMessage('EURISERINOMINA from=' + encodeURIComponent(path));
			var format = ext.replace('.', '').toLowerCase();
			if (app.socket && app.socket.sendMessage) {
				app.socket.sendMessage('saveas url=' + encodeURI('file://' + target) + ' format=' + format + ' options=');
				notice(t('Rinomino in ', 'Renaming to ') + wanted + ext);
			}
		};
		input.oninput = function () { input.size = Math.max(6, input.value.length + 1); };
		input.onkeydown = function (e) {
			e.stopPropagation();
			if (e.key === 'Enter') { e.preventDefault(); finish(true); }
			else if (e.key === 'Escape') { e.preventDefault(); finish(false); }
		};
		input.onblur = function () { finish(false); };
		input.focus();
		input.select();
	}

	// --- Nastri completi del computer (tavola OffPCNastro): sette schede come Word, più la Suite ------------------------

	/** Voce col comando del motore (anche con parametri) e l'icona del comando indicato. */
	function u(it, en, command, big, iconCommand) {
		var item = r(it, en, command, big);
		item.icon = iconCommand || String(command).split('?')[0];
		return item;
	}
	/** Piccola con il nome (tre righe per gruppo, come Word). */
	function ul(it, en, command, iconCommand) { return lab(u(it, en, command, false, iconCommand)); }
	/** Voce che apre una tendina di scelte. */
	function um(item, entries) { item.run = menuOf(entries); item.menu = true; return item; }
	function rows(group, list) { group.rows = list; return group; }
	function more(group, command) { group.deskMore = command; return group; }
	function fn(name) { return function () { dispatch(name); }; }

	var CASE_MENU = [[t('MAIUSCOLO', 'UPPERCASE'), '.uno:ChangeCaseToUpper'], [t('minuscolo', 'lowercase'), '.uno:ChangeCaseToLower'],
		[t('Prima maiuscola nella frase', 'Sentence case'), '.uno:ChangeCaseToSentenceCase'], [t('Ogni Parola Maiuscola', 'Capitalize Every Word'), '.uno:ChangeCaseToTitleCase']];
	var SPACING_MENU = [['1,0', '.uno:SpacePara1'], ['1,15', '.uno:SpacePara115'], ['1,5', '.uno:SpacePara15'], ['2,0', '.uno:SpacePara2'],
		[t('Più spazio tra i paragrafi', 'More space between paragraphs'), '.uno:ParaspaceIncrease'],
		[t('Meno spazio tra i paragrafi', 'Less space between paragraphs'), '.uno:ParaspaceDecrease'],
		[t('Rientri e spaziatura…', 'Indents and spacing…'), '.uno:ParagraphDialog']];
	var SHAPES_MENU = [[t('Rettangolo', 'Rectangle'), '.uno:BasicShapes.rectangle'], [t('Rettangolo arrotondato', 'Rounded rectangle'), '.uno:BasicShapes.round-rectangle'],
		[t('Ellisse', 'Ellipse'), '.uno:BasicShapes.ellipse'], [t('Linea', 'Line'), '.uno:Line'], [t('Freccia', 'Arrow'), '.uno:ArrowShapes.right-arrow'],
		[t('Fumetto', 'Callout'), '.uno:CalloutShapes.round-rectangular-callout'], [t('Stella', 'Star'), '.uno:StarShapes.star5']];
	var WRAP_MENU = [[t('Nessuno', 'None'), '.uno:WrapOff'], [t('Ai lati', 'Parallel'), '.uno:WrapOn'], [t('Ottimale', 'Optimal'), '.uno:WrapIdeal'],
		[t('A sinistra', 'Before'), '.uno:WrapLeft'], [t('A destra', 'After'), '.uno:WrapRight'], [t('Attraverso', 'Through'), '.uno:WrapThrough']];
	function inchToHmm(v) { return Math.round(v * 2540); }
	function marginsMenu() {
		var calc = app.map.getDocType() === 'spreadsheet';
		var set = function (top, side) {
			return function () {
				if (calc) {
					uno('.uno:CalcPageMargin?Page.LeftMargin:long=' + inchToHmm(side) + '&Page.RightMargin=' + inchToHmm(side) +
						'&Page.TopMargin:long=' + inchToHmm(top) + '&Page.BottomMargin=' + inchToHmm(top));
				} else {
					uno('.uno:PageLRMargin?Page.Left:long=' + inchToHmm(side) + '&Page.Right:long=' + inchToHmm(side));
					uno('.uno:PageULMargin?Page.Upper:long=' + inchToHmm(top) + '&Page.Lower:long=' + inchToHmm(top));
				}
			};
		};
		return [[t('Normali · 2 cm', 'Normal · 2 cm'), set(0.79, 0.79)], [t('Stretti · 1,27 cm', 'Narrow · 1.27 cm'), set(0.5, 0.5)],
			[t('Larghi · 2,54 / 5,08 cm', 'Wide · 2.54 / 5.08 cm'), set(1, 2)],
			[t('Personalizzati…', 'Custom…'), calc ? '.uno:PageFormatDialog' : '.uno:PageDialog']];
	}
	var ORIENT_MENU = [[t('Verticale', 'Portrait'), '.uno:Orientation?isLandscape:bool=false'], [t('Orizzontale', 'Landscape'), '.uno:Orientation?isLandscape:bool=true']];
	function sizesMenu() {
		var calc = app.map.getDocType() === 'spreadsheet';
		// Numeri del formato della carta come in Definitions.Menu.ts (enum Paper): A3 3, A4 4, A5 5, Letter 8, Legal 9.
		var size = function (paper) { return calc ? '.uno:CalcPageSize?PaperFormat:long=' + paper : '.uno:AttributePageSize?PaperFormat:short=' + paper; };
		return [['A4 · 21 × 29,7 cm', size(4)], ['A5 · 14,8 × 21 cm', size(5)], ['A3 · 29,7 × 42 cm', size(3)],
			[t('Lettera', 'Letter') + ' · 21,6 × 27,9 cm', size(8)], [t('Legale', 'Legal') + ' · 21,6 × 35,6 cm', size(9)],
			[t('Altre dimensioni…', 'More sizes…'), calc ? '.uno:PageFormatDialog' : '.uno:PageDialog']];
	}
	var FIELDS_MENU = [[t('Data', 'Date'), '.uno:InsertDateField'], [t('Ora', 'Time'), '.uno:InsertTimeField'], [t('Numero di pagina', 'Page number'), '.uno:InsertPageNumberField'],
		[t('Numero di pagine', 'Page count'), '.uno:InsertPageCountField'], [t('Titolo', 'Title'), '.uno:InsertTitleField'], [t('Altri campi…', 'More fields…'), '.uno:InsertField']];

	var D_APPUNTI = g('Appunti', 'Clipboard', [u('Incolla', 'Paste', '.uno:Paste', 'big'), ul('Taglia', 'Cut', '.uno:Cut'), ul('Copia', 'Copy', '.uno:Copy'),
		ul('Copia formato', 'Clone formatting', '.uno:FormatPaintbrush')]);
	function deskFont(docType) {
		var back = docType === 'spreadsheet' ? colorItem('Riempimento', 'Fill', 'back', '.uno:BackgroundColor') : colorItem('Evidenzia', 'Highlight', 'back', '.uno:CharBackColor');
		var group = g('Carattere', 'Font', [u('Grassetto', 'Bold', '.uno:Bold'), u('Corsivo', 'Italic', '.uno:Italic'), u('Sottolineato', 'Underline', '.uno:Underline'),
			u('Barrato', 'Strikethrough', '.uno:Strikeout'), u('Pedice', 'Subscript', '.uno:SubScript'), u('Apice', 'Superscript', '.uno:SuperScript'),
			u('Ombra', 'Shadow', '.uno:Shadowed'), back, colorItem('Colore del carattere', 'Font color', 'font', '.uno:Color')], '.uno:FontDialog');
		group.fonts = true;
		group.fontExtras = [u('Ingrandisci', 'Grow', '.uno:Grow'), u('Riduci', 'Shrink', '.uno:Shrink'),
			um(u('Maiuscole e minuscole', 'Change case', null, false, '.uno:ChangeCaseRotateCase'), CASE_MENU),
			ri('Cancella formattazione', 'Clear formatting', clearFormatting, false, docType === 'text' ? '.uno:ResetAttributes' : '.uno:SetDefault')];
		return group;
	}
	var D_PARAGRAFO = more(rows(g('Paragrafo', 'Paragraph', []), [
		[u('Elenco puntato', 'Bullets', '.uno:DefaultBullet'), u('Elenco numerato', 'Numbering', '.uno:DefaultNumbering'), u('Elenco a più livelli', 'Outline', '.uno:OutlineBullet'),
			u('Riduci rientro', 'Decrease indent', '.uno:DecrementIndent'), u('Aumenta rientro', 'Increase indent', '.uno:IncrementIndent'), u('Ordina', 'Sort', '.uno:SortDialog', false, '.uno:SortAscending'),
			u('Mostra segni di formattazione', 'Formatting marks', '.uno:ControlCodes')],
		[u('A sinistra', 'Left', '.uno:LeftPara'), u('Al centro', 'Center', '.uno:CenterPara'), u('A destra', 'Right', '.uno:RightPara'), u('Giustificato', 'Justified', '.uno:JustifyPara'),
			um(u('Interlinea', 'Line spacing', null, false, '.uno:LineSpacing'), SPACING_MENU), u('Da sinistra a destra', 'Left to right', '.uno:ParaLeftToRight'),
			u('Da destra a sinistra', 'Right to left', '.uno:ParaRightToLeft')]]), '.uno:ParagraphDialog');
	var D_STILI = g('Stili', 'Styles', [r('Normale', 'Normal', style('Standard'), 'big'), r('Titolo', 'Title', style('Title'), 'big'),
		r('Titolo 1', 'Heading 1', style('Heading 1'), 'big'), r('Titolo 2', 'Heading 2', style('Heading 2'), 'big')]);
	// Citazione e gli altri stili sono in ↗ (colonna Stili): con cinque tessere la Suite non ci stava a 1440 px.
	D_STILI.tiles = true;
	D_STILI.deskMore = '.uno:SidebarDeck.StyleListDeck';
	var D_MODIFICA = g('Modifica', 'Editing', [ul('Trova', 'Find', function () { deskFind(false); }, '.uno:SearchDialog'),
		ul('Sostituisci', 'Replace', function () { deskFind(true); }, '.uno:EuriseReplace'), ul('Seleziona tutto', 'Select all', '.uno:SelectAll')]);
	var D_COMMENTO = u('Commento', 'Comment', fn('insertcomment'), 'big', '.uno:InsertAnnotation');
	var D_IMMAGINE = u('Immagine', 'Image', fn('localgraphic'), 'big', '.uno:InsertGraphic');
	var D_TABELLA = u('Tabella', 'Table', TABELLA.run, 'big', '.uno:InsertTable');
	var D_FORME = um(u('Forme', 'Shapes', null, 'big', '.uno:BasicShapes'), SHAPES_MENU);
	var D_GRAFICO = u('Grafico', 'Chart', '.uno:InsertObjectChart', 'big');
	var D_LINK = ul('Collegamento', 'Link', fn('hyperlinkdialog'), '.uno:HyperlinkDialog');
	var D_ZOOM = g('Zoom', 'Zoom', [u('Ingrandisci', 'Zoom in', fn('zoomin'), 'big', '.uno:ZoomPlus'), u('Riduci', 'Zoom out', fn('zoomout'), 'big', '.uno:ZoomMinus'),
		ul('100%', '100%', fn('zoomreset'), '.uno:EuriseZoom100'), ul('Larghezza pagina', 'Page width', fn('fitwidthzoom'), '.uno:EuriseZoomWidth')]);
	var D_ASPETTO = g('Aspetto', 'Appearance', [u('Concentrazione', 'Focus', function () { setFocusMode(true); }, 'big', '.uno:EuriseConcentrazione'),
		u('Tema Notte', 'Night theme', fn('toggledarktheme'), 'big', '.uno:EuriseNotte'),
		ul('Nascondi il nastro', 'Hide the ribbon', function () { setCollapsed(true); }, '.uno:EuriseNastro'), ul('Schermo intero', 'Full screen', '.uno:FullScreen')]);

	var DESK_RIBBONS = {
		text: [
			{ id: 'home', label: 'Home', groups: [D_APPUNTI, deskFont('text'), D_PARAGRAFO, D_STILI, yours(D_MODIFICA.items), SUITE_HOME] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), groups: [
				g('Pagine', 'Pages', [u('Frontespizio', 'Title page', '.uno:TitlePageDialog', 'big'), ul('Interruzione di pagina', 'Page break', '.uno:InsertPagebreak'),
					ul('Altre interruzioni…', 'More breaks…', '.uno:InsertBreak'), ul('Sezione', 'Section', '.uno:InsertSection')]),
				g('Tabelle', 'Tables', [D_TABELLA]),
				g('Illustrazioni', 'Illustrations', [D_IMMAGINE, D_FORME, D_GRAFICO, ul('Fontwork', 'Fontwork', '.uno:FontworkGalleryFloater'),
					ul('Codice QR', 'QR code', '.uno:InsertQrCode'), ul('Cornice', 'Frame', '.uno:InsertFrame')]),
				g('Collegamenti', 'Links', [D_LINK, ul('Segnalibro', 'Bookmark', '.uno:InsertBookmark'), ul('Rimando', 'Cross-reference', '.uno:InsertReferenceField')]),
				g('Commenti', 'Comments', [D_COMMENTO]),
				g('Intestazione e piè', 'Header and footer', [ul('Intestazione', 'Header', '.uno:InsertPageHeader?On:bool=true'),
					ul('Piè di pagina', 'Footer', '.uno:InsertPageFooter?On:bool=true'), ul('Numero di pagina', 'Page number', '.uno:PageNumberWizard')]),
				g('Testo', 'Text', [u('Casella di testo', 'Text box', '.uno:DrawText?CreateDirectly:bool=true', 'big'),
					um(ul('Campo', 'Field', null, '.uno:InsertFieldCtrl'), FIELDS_MENU), ul('Data', 'Date', '.uno:InsertDateField'), ul('Riga per la firma', 'Signature line', '.uno:InsertSignatureLine')]),
				g('Simboli', 'Symbols', [ul('Formula', 'Formula', '.uno:InsertObjectStarMath'), ul('Simbolo', 'Symbol', '.uno:CharmapControl'), ul('Nota a piè', 'Footnote', '.uno:InsertFootnote')])] },
			// Progetta e Layout in una scheda sola (proposta 6 del 15 novembre): tutto ciò che riguarda la pagina.
			{ id: 'pagina', label: t('Pagina', 'Page'), groups: [
				more(g('Imposta pagina', 'Page setup', [um(u('Margini', 'Margins', null, 'big', '.uno:EurisePageMargin'), marginsMenu),
					um(u('Orientamento', 'Orientation', null, 'big', '.uno:Orientation'), ORIENT_MENU), um(u('Dimensioni', 'Size', null, 'big', '.uno:EurisePageSize'), sizesMenu),
					u('Colonne', 'Columns', '.uno:FormatColumns', 'big'), ul('Interruzioni…', 'Breaks…', '.uno:InsertBreak'),
					ul('Numeri di riga', 'Line numbers', '.uno:LineNumberingDialog'), ul('Sillabazione', 'Hyphenation', '.uno:Hyphenate')]), '.uno:PageDialog'),
				g('Aspetto della pagina', 'Page design', [u('Temi', 'Themes', '.uno:ThemeDialog', 'big'), u('Filigrana', 'Watermark', '.uno:Watermark', 'big'),
					ul('Colore e bordi', 'Colour and borders', '.uno:PageDialog', '.uno:EurisePageColor'), ul('Nuovo stile', 'New style', '.uno:StyleNewByExample'),
					ul('Aggiorna lo stile', 'Update style', '.uno:StyleUpdateByExample')]),
				g('Paragrafo', 'Paragraph', [ul('Rientri e spaziatura…', 'Indents and spacing…', '.uno:ParagraphDialog'), ul('Più spazio', 'More space', '.uno:ParaspaceIncrease'),
					ul('Meno spazio', 'Less space', '.uno:ParaspaceDecrease')]),
				g('Disponi', 'Arrange', [um(u('A capo', 'Wrap', null, 'big', '.uno:EuriseWrap'), WRAP_MENU), ul('Porta avanti', 'Bring forward', '.uno:ObjectForwardOne'),
					ul('Porta indietro', 'Send backward', '.uno:ObjectBackOne'), ul('In primo piano', 'Bring to front', '.uno:BringToFront'),
					ul('Raggruppa', 'Group', '.uno:FormatGroup'), ul('Posizione e dimensioni', 'Position and size', '.uno:TransformDialog')])] },
			{ id: 'riferimenti', label: t('Riferimenti', 'References'), groups: [
				g('Sommario', 'Table of contents', [u('Sommario', 'Table of contents', '.uno:InsertMultiIndex', 'big'), ul('Aggiorna il sommario', 'Update', '.uno:UpdateCurIndex'),
					ul('Aggiorna tutto', 'Update all', '.uno:UpdateAll')]),
				g('Note a piè di pagina', 'Footnotes', [u('Nota a piè', 'Footnote', '.uno:InsertFootnote', 'big'), ul('Nota di chiusura', 'Endnote', '.uno:InsertEndnote'),
					ul('Impostazioni delle note', 'Footnote settings', '.uno:FootnoteDialog')]),
				g('Didascalie', 'Captions', [u('Didascalia', 'Caption', '.uno:InsertCaptionDialog', 'big'), ul('Rimando', 'Cross-reference', '.uno:InsertReferenceField'),
					ul('Segnalibro', 'Bookmark', '.uno:InsertBookmark')]),
				g('Indice analitico', 'Index', [u('Segna voce', 'Mark entry', '.uno:InsertIndexesEntry', 'big'), ul('Inserisci indice', 'Insert index', '.uno:InsertMultiIndex')])] },
			{ id: 'revisione', label: t('Revisione', 'Review'), groups: [
				g('Strumenti di correzione', 'Proofing', [u('Ortografia e grammatica', 'Spelling and grammar', '.uno:SpellingAndGrammarDialog', 'big'),
					ul('Sinonimi', 'Thesaurus', '.uno:ThesaurusDialog'), ul('Conteggio parole', 'Word count', '.uno:WordCountDialog'), ul('Accessibilità', 'Accessibility', '.uno:AccessibilityCheck')]),
				g('Lingua', 'Language', [u('Lingua', 'Language', fn('languagemenu'), 'big', '.uno:LanguageMenu')]),
				g('Commenti', 'Comments', [u('Nuovo commento', 'New comment', fn('insertcomment'), 'big', '.uno:InsertAnnotation'),
					ul('Elimina tutti', 'Delete all', '.uno:DeleteAllNotes'), ul('Mostra commenti', 'Show comments', fn('showannotations'), '.uno:ShowAnnotations')]),
				g('Rilevamento', 'Tracking', [u('Registra le modifiche', 'Record changes', '.uno:TrackChanges', 'big'), ul('Mostra le modifiche', 'Show changes', '.uno:ShowTrackedChanges'),
					ul('Gestisci', 'Manage', '.uno:AcceptTrackedChanges')]),
				g('Modifiche', 'Changes', [u('Accetta', 'Accept', '.uno:AcceptTrackedChange', 'big'), u('Rifiuta', 'Reject', '.uno:RejectTrackedChange', 'big'),
					ul('Precedente', 'Previous', '.uno:PreviousTrackedChange'), ul('Successiva', 'Next', '.uno:NextTrackedChange'), ul('Accetta tutte', 'Accept all', '.uno:AcceptAllTrackedChanges')]),
				g('Confronta e proteggi', 'Compare and protect', [ul('Confronta…', 'Compare…', fn('localcomparedocuments'), '.uno:CompareDocuments'),
					ul('Proteggi le revisioni', 'Protect changes', '.uno:ProtectTraceChangeMode')])] },
			{ id: 'vista', label: t('Vista', 'View'), groups: [
				g('Mostra', 'Show', [ul('Righello', 'Ruler', fn('showruler'), '.uno:Ruler'), ul('Segni di formattazione', 'Formatting marks', '.uno:ControlCodes'),
					ul('Limiti del testo', 'Text boundaries', '.uno:ShowBoundaries'), ul('Più pagine', 'Multiple pages', fn('multipageview'), '.uno:MultiPageView'),
					ul('Barra laterale', 'Sidebar', '.uno:Sidebar')]),
				D_ZOOM, D_ASPETTO] },
			{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE] }
		],
		spreadsheet: [
			{ id: 'home', label: 'Home', groups: [D_APPUNTI, deskFont('spreadsheet'),
				more(rows(g('Allineamento', 'Alignment', []), [
					[u('In alto', 'Top', '.uno:CellVertTop'), u('Al centro', 'Middle', '.uno:CellVertCenter'), u('In basso', 'Bottom', '.uno:CellVertBottom'),
						u('Testo a capo', 'Wrap text', '.uno:WrapText'), u('Unisci e centra', 'Merge and center', '.uno:ToggleMergeCells')],
					[u('A sinistra', 'Left', '.uno:AlignLeft'), u('Al centro', 'Center', '.uno:AlignHorizontalCenter'), u('A destra', 'Right', '.uno:AlignRight'),
						u('Giustificato', 'Justified', '.uno:AlignBlock'), u('Riduci rientro', 'Decrease indent', '.uno:DecrementIndent'), u('Aumenta rientro', 'Increase indent', '.uno:IncrementIndent')]]), '.uno:FormatCellDialog'),
				more(rows(g('Numeri', 'Numbers', []), [
					[u('Valuta', 'Currency', '.uno:NumberFormatCurrency'), u('Percentuale', 'Percent', '.uno:NumberFormatPercent'), u('Separatore delle migliaia', 'Thousands', '.uno:NumberFormatThousands')],
					[u('Data', 'Date', '.uno:NumberFormatDate'), u('Aggiungi decimale', 'Add decimal', '.uno:NumberFormatIncDecimals'), u('Togli decimale', 'Remove decimal', '.uno:NumberFormatDecDecimals')]]), '.uno:FormatCellDialog'),
				g('Celle', 'Cells', [um(ul('Inserisci', 'Insert', null, '.uno:InsertRowsBefore'), [[t('Riga sopra', 'Row above'), '.uno:InsertRowsBefore'], [t('Riga sotto', 'Row below'), '.uno:InsertRowsAfter'],
					[t('Colonna prima', 'Column before'), '.uno:InsertColumnsBefore'], [t('Colonna dopo', 'Column after'), '.uno:InsertColumnsAfter']]),
					um(ul('Elimina', 'Delete', null, '.uno:DeleteRows'), [[t('Righe', 'Rows'), '.uno:DeleteRows'], [t('Colonne', 'Columns'), '.uno:DeleteColumns']]),
					ul('Formato celle…', 'Format cells…', '.uno:FormatCellDialog', '.uno:PageDialog')]),
				yours([ul('Somma', 'Sum', '.uno:AutoSum'), ul('Ordina', 'Sort', '.uno:DataSort'), ul('Filtro', 'Filter', '.uno:DataFilterAutoFilter')]),
				SUITE_FOGLIO] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), groups: [
				g('Tabelle', 'Tables', [u('Tabella pivot', 'Pivot table', '.uno:DataDataPilotRun', 'big'), u('Tabella', 'Table', '.uno:InsertCalcTable', 'big', '.uno:InsertTable')]),
				g('Illustrazioni', 'Illustrations', [D_IMMAGINE, D_FORME, D_GRAFICO, ul('Fontwork', 'Fontwork', '.uno:FontworkGalleryFloater')]),
				g('Collegamenti', 'Links', [D_LINK, D_COMMENTO]),
				g('Testo', 'Text', [u('Casella di testo', 'Text box', '.uno:DrawText?CreateDirectly:bool=true', 'big'), ul('Simbolo', 'Symbol', '.uno:CharmapControl'),
					ul('Funzione…', 'Function…', '.uno:FunctionDialog')]),
				g('Fogli', 'Sheets', [u('Nuovo foglio', 'New sheet', fn('insertsheet'), 'big', '.uno:Insert')])] },
			{ id: 'pagina', label: t('Pagina', 'Page'), groups: [
				more(g('Imposta pagina', 'Page setup', [um(u('Margini', 'Margins', null, 'big', '.uno:EurisePageMargin'), marginsMenu),
					um(u('Orientamento', 'Orientation', null, 'big', '.uno:Orientation'), ORIENT_MENU), um(u('Dimensioni', 'Size', null, 'big', '.uno:EurisePageSize'), sizesMenu),
					ul('Area di stampa', 'Print area', '.uno:DefinePrintArea'), ul('Intestazione e piè', 'Header and footer', '.uno:EditHeaderAndFooter'),
					ul('Griglia di stampa', 'Print grid', '.uno:TogglePrintGrid')]), '.uno:PageFormatDialog')] },
			{ id: 'formule', label: t('Formule', 'Formulas'), groups: [
				g('Funzioni', 'Functions', [u('Somma', 'Sum', '.uno:AutoSum', 'big'), u('Funzione', 'Function', '.uno:FunctionDialog', 'big')]),
				g('Nomi', 'Names', [ul('Definisci nome', 'Define name', '.uno:DefineName'), ul('Nome per area dati', 'Database range', '.uno:DefineDBName')]),
				g('Calcolo', 'Calculation', [u('Ricalcola', 'Recalculate', '.uno:Calculate', 'big'), ul('Ricalcola tutto', 'Recalculate all', '.uno:CalculateHard'),
					ul('Ricerca obiettivo', 'Goal seek', '.uno:GoalSeekDialog'), ul('Risolutore', 'Solver', '.uno:SolverDialog')])] },
			{ id: 'dati', label: t('Dati', 'Data'), groups: [
				g('Ordina e filtra', 'Sort and filter', [u('Ordina', 'Sort', '.uno:DataSort', 'big'), u('Filtro', 'Filter', '.uno:DataFilterAutoFilter', 'big'),
					ul('Dalla A alla Z', 'A to Z', '.uno:SortAscending'), ul('Dalla Z alla A', 'Z to A', '.uno:SortDescending'), ul('Filtro standard', 'Standard filter', '.uno:DataFilterStandardFilter'),
					ul('Togli il filtro', 'Remove filter', '.uno:DataFilterRemoveFilter')]),
				g('Strumenti dei dati', 'Data tools', [ul('Testo in colonne', 'Text to columns', '.uno:TextToColumns'), ul('Doppioni', 'Duplicates', '.uno:HandleDuplicateRecords'),
					ul('Convalida', 'Validity', '.uno:Validation'), ul('Subtotali', 'Subtotals', '.uno:DataSubTotals'), ul('Tabella pivot', 'Pivot table', '.uno:DataDataPilotRun')]),
				g('Struttura', 'Outline', [ul('Raggruppa', 'Group', '.uno:Group'), ul('Separa', 'Ungroup', '.uno:Ungroup'), ul('Mostra dettagli', 'Show details', '.uno:ShowDetail'),
					ul('Nascondi dettagli', 'Hide details', '.uno:HideDetail')])] },
			{ id: 'revisione', label: t('Revisione', 'Review'), groups: [
				g('Correzione', 'Proofing', [u('Ortografia', 'Spelling', '.uno:SpellDialog', 'big')]),
				g('Commenti', 'Comments', [D_COMMENTO, ul('Elimina tutti', 'Delete all', '.uno:DeleteAllNotes')]),
				g('Modifiche', 'Changes', [u('Registra', 'Record', '.uno:TraceChangeMode', 'big', '.uno:TrackChanges'), ul('Gestisci', 'Manage', '.uno:AcceptChanges')]),
				g('Proteggi', 'Protect', [u('Proteggi il foglio', 'Protect sheet', '.uno:Protect', 'big')])] },
			{ id: 'vista', label: t('Vista', 'View'), groups: [
				g('Blocca', 'Freeze', [u('Blocca righe e colonne', 'Freeze panes', '.uno:FreezePanes', 'big'), ul('Prima riga', 'First row', '.uno:FreezePanesRow'),
					ul('Prima colonna', 'First column', '.uno:FreezePanesColumn')]),
				g('Mostra', 'Show', [ul('Griglia', 'Grid', '.uno:ToggleSheetGrid'), ul('Intestazioni', 'Headers', '.uno:ViewRowColumnHeaders'),
					ul('Evidenzia riga e colonna', 'Highlight row and column', fn('columnrowhighlight'), '.uno:EuriseHighlight')]),
				D_ZOOM, D_ASPETTO] },
			{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE_FOGLIO] }
		],
		presentation: [
			{ id: 'home', label: 'Home', groups: [D_APPUNTI,
				g('Diapositive', 'Slides', [u('Nuova', 'New', '.uno:InsertPage', 'big', '.uno:InsertSlide'), ul('Duplica', 'Duplicate', '.uno:DuplicatePage', '.uno:DuplicateSlide'),
					ul('Elimina', 'Delete', '.uno:DeletePage', '.uno:DeleteSlide'), ul('Layout', 'Layout', '.uno:AssignLayout', '.uno:EuriseLayout')]),
				deskFont('presentation'),
				more(rows(g('Paragrafo', 'Paragraph', []), [
					[u('Elenco puntato', 'Bullets', '.uno:DefaultBullet'), u('Elenco numerato', 'Numbering', '.uno:DefaultNumbering'),
						u('Riduci rientro', 'Decrease indent', '.uno:DecrementIndent'), u('Aumenta rientro', 'Increase indent', '.uno:IncrementIndent')],
					[u('A sinistra', 'Left', '.uno:LeftPara'), u('Al centro', 'Center', '.uno:CenterPara'), u('A destra', 'Right', '.uno:RightPara'),
						u('Giustificato', 'Justified', '.uno:JustifyPara'), um(u('Interlinea', 'Line spacing', null, false, '.uno:LineSpacing'), SPACING_MENU)]]), '.uno:ParagraphDialog'),
				g('Disegno', 'Drawing', [D_FORME, u('Casella di testo', 'Text box', '.uno:Text?CreateDirectly:bool=true', 'big', '.uno:Text'),
					ul('In primo piano', 'Bring to front', '.uno:BringToFront'), ul('Sullo sfondo', 'Send to back', '.uno:SendToBack'), ul('Raggruppa', 'Group', '.uno:FormatGroup')]),
				yours(D_MODIFICA.items), SUITE_HOME] },
			{ id: 'inserisci', label: t('Inserisci', 'Insert'), groups: [
				g('Diapositive', 'Slides', [u('Nuova diapositiva', 'New slide', '.uno:InsertPage', 'big', '.uno:InsertSlide'), ul('Duplica', 'Duplicate', '.uno:DuplicatePage', '.uno:DuplicateSlide')]),
				g('Tabelle', 'Tables', [D_TABELLA]),
				g('Illustrazioni', 'Illustrations', [D_IMMAGINE, D_FORME, D_GRAFICO, ul('Fontwork', 'Fontwork', '.uno:FontworkGalleryFloater'),
					ul('Audio e video', 'Audio and video', fn('insertmultimedia'), '.uno:InsertAVMedia')]),
				g('Collegamenti', 'Links', [D_LINK, D_COMMENTO]),
				g('Testo', 'Text', [u('Casella di testo', 'Text box', '.uno:Text?CreateDirectly:bool=true', 'big', '.uno:Text'), ul('Intestazione e piè', 'Header and footer', '.uno:HeaderAndFooter'),
					ul('Numero della diapositiva', 'Slide number', '.uno:InsertSlideField'), ul('Data', 'Date', '.uno:InsertDateFieldFix', '.uno:InsertDateField'), ul('Simbolo', 'Symbol', '.uno:CharmapControl')])] },
			{ id: 'diapositiva', label: t('Diapositiva', 'Slide'), groups: [
				g('Diapositiva', 'Slide', [u('Dimensioni', 'Size', '.uno:SlideSetup', 'big', '.uno:EurisePageSize'), u('Sfondo', 'Background', fn('selectbackground'), 'big', '.uno:EurisePageColor')]),
				g('Schema', 'Master', [u('Schema diapositiva', 'Slide master', '.uno:SlideMasterPage', 'big', '.uno:EuriseLayout'), ul('Chiudi lo schema', 'Close master', '.uno:CloseMasterView')])] },
			{ id: 'transizioni', label: t('Transizioni', 'Transitions'), groups: [
				g('Transizioni', 'Transitions', [u('Transizione', 'Transition', fn('transitiondeck'), 'big', '.uno:EuriseTransizione')])] },
			{ id: 'animazioni', label: t('Animazioni', 'Animations'), groups: [
				g('Animazioni', 'Animations', [u('Animazione', 'Animation', fn('animationdeck'), 'big', '.uno:EuriseAnimazione')])] },
			{ id: 'presenta', label: t('Presenta', 'Present'), groups: [
				g('Presentazione', 'Slideshow', [u('Dall\'inizio', 'From start', fn('presentation'), 'big', '.uno:Presentation'),
					u('Dalla corrente', 'From current', fn('presentation-currentslide'), 'big', '.uno:Presentation'),
					ul('In una finestra', 'In a window', fn('presentinwindow'), '.uno:EurisePresentaFinestra'), ul('Console del relatore', 'Presenter console', fn('presenterconsole'), '.uno:EuriseConsole')]),
				g('Diapositive', 'Slides', [ul('Nascondi', 'Hide', fn('hideslide'), '.uno:HideSlide'), ul('Mostra', 'Show', fn('showslide'), '.uno:ShowSlide')])] },
			{ id: 'revisione', label: t('Revisione', 'Review'), groups: [
				g('Correzione', 'Proofing', [u('Ortografia', 'Spelling', '.uno:SpellDialog', 'big')]),
				g('Commenti', 'Comments', [D_COMMENTO, ul('Elimina tutti', 'Delete all', '.uno:DeleteAllAnnotation', '.uno:DeleteAllNotes')])] },
			{ id: 'vista', label: t('Vista', 'View'), groups: [
				g('Viste', 'Views', [u('Normale', 'Normal', '.uno:NormalMultiPaneGUI', 'big'), u('Note', 'Notes', '.uno:NotesMode', 'big'), u('Sequenza', 'Slide sorter', '.uno:DiaMode', 'big')]),
				D_ZOOM, D_ASPETTO] },
			{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE] }
		]
	};
	DESK_RIBBONS.drawing = DESK_RIBBONS.presentation;
	DESK_RIBBONS.pdf = [
		{ id: 'pdf', label: 'PDF', groups: [
			g('Documento', 'Document', [u('Stampa', 'Print', fn('print'), 'big', '.uno:Print'), u('Salva una copia', 'Save a copy', function () { shellCommand('.uno:SaveAs'); }, 'big', '.uno:SaveAs'),
				ul('Trova', 'Find', function () { deskFind(false); }, '.uno:SearchDialog'), ul('Proprietà', 'Properties', '.uno:SetDocumentProperties', '.uno:EuriseInfo')]),
			D_ZOOM, D_ASPETTO] },
		{ id: 'suite', label: 'Suite', suite: true, groups: [SUITE] }
	];

	// =================================================================================================================
	// Ergonomia del computer (dieci proposte approvate dal proprietario il 15 novembre): i comandi vanno dove si lavora,
	// sotto le dita e vicino al puntatore; meno schede e meno doppioni; il nastro si toglie quando serve concentrarsi.
	// =================================================================================================================

	// --- Quante volte si usa un comando: serve a «I tuoi comandi» e all'ordine di Ctrl K (proposte 5 e 8) ---------------

	var USE_KEY = 'eurise-usi';
	var DESK_ITEMS = {};

	function useCounts() {
		try { return JSON.parse(window.localStorage.getItem(USE_KEY) || '{}') || {}; } catch (e) { return {}; }
	}

	function countUse(item) {
		if (!item || !item.label || item.menu) return;
		var counts = useCounts();
		counts[item.label] = (counts[item.label] || 0) + 1;
		try { window.localStorage.setItem(USE_KEY, JSON.stringify(counts)); } catch (e) { /* niente memoria */ }
	}

	/** Tutte le voci dei nastri del computer, per nome: Ctrl K le trova anche se la scheda non è aperta. */
	function indexDeskItems(list) {
		DESK_ITEMS = {};
		list.forEach(function (tab) {
			tab.groups.forEach(function (group) {
				var all = (group.items || []).concat(group.fontExtras || []);
				(group.rows || []).forEach(function (r) { all = all.concat(r); });
				all.forEach(function (item) {
					if (item && item.label && item.run && !item.menu && !DESK_ITEMS[item.label]) DESK_ITEMS[item.label] = { item: item, where: tab.label };
				});
			});
		});
	}

	/** Gruppo «I tuoi comandi»: parte dai comandi indicati e, giorno per giorno, prende quelli che usi di più. */
	function yours(defaults) {
		var group = g('I tuoi comandi', 'Your commands', defaults);
		group.yours = true;
		group.defaults = defaults;
		return group;
	}

	function yourItems(group) {
		var today = new Date().toISOString().slice(0, 10);
		var key = 'eurise-tuoi-' + app.map.getDocType();
		var snap = null;
		try { snap = JSON.parse(window.localStorage.getItem(key) || 'null'); } catch (e) { snap = null; }
		if (!snap || snap.day !== today) {
			// Al massimo un cambio al giorno: la mano trova i pulsanti dove li ha lasciati.
			var counts = useCounts();
			var top = Object.keys(counts).filter(function (label) { return counts[label] >= 3 && DESK_ITEMS[label]; })
				.sort(function (a, b) { return counts[b] - counts[a]; });
			var labels = top.slice(0, 3);
			group.defaults.forEach(function (d) { if (labels.length < 3 && labels.indexOf(d.label) < 0) labels.push(d.label); });
			snap = { day: today, labels: labels };
			try { window.localStorage.setItem(key, JSON.stringify(snap)); } catch (e) { /* niente memoria */ }
		}
		return snap.labels.map(function (label) {
			var found = DESK_ITEMS[label] && DESK_ITEMS[label].item;
			if (!found) group.defaults.forEach(function (d) { if (d.label === label) found = d; });
			if (!found) return null;
			// Nel gruppo sono tutte piccole con il nome, una sotto l'altra.
			var copy = {};
			Object.keys(found).forEach(function (k) { copy[k] = found[k]; });
			copy.big = false;
			copy.desk = 'lab';
			return copy;
		}).filter(Boolean);
	}

	var SHORTCUTS = {
		'.uno:Bold': 'Ctrl B', '.uno:Italic': 'Ctrl I', '.uno:Underline': 'Ctrl U', '.uno:Cut': 'Ctrl X', '.uno:Copy': 'Ctrl C', '.uno:Paste': 'Ctrl V',
		'.uno:SelectAll': 'Ctrl A', '.uno:Undo': 'Ctrl Z', '.uno:Redo': 'Ctrl Y', '.uno:InsertPagebreak': 'Ctrl Invio', '.uno:LeftPara': 'Ctrl L',
		'.uno:CenterPara': 'Ctrl E', '.uno:RightPara': 'Ctrl R', '.uno:JustifyPara': 'Ctrl J', '.uno:SuperScript': 'Ctrl Maiusc P', '.uno:SubScript': 'Ctrl Maiusc B',
		'.uno:Grow': 'Ctrl ]', '.uno:Shrink': 'Ctrl [', '.uno:SpellingAndGrammarDialog': 'F7', '.uno:SpellDialog': 'F7', '.uno:InsertAnnotation': 'Ctrl Alt C',
		'.uno:FullScreen': 'Ctrl Maiusc J', '.uno:AutoSum': 'Alt =', '.uno:ControlCodes': 'Ctrl F10'
	};

	function shortcutOf(item) {
		var command = typeof item.run === 'string' ? item.run.split('?')[0] : item.icon;
		if (item.label === t('Trova', 'Find')) return 'Ctrl F';
		if (item.label === t('Sostituisci', 'Replace')) return 'Ctrl H';
		return SHORTCUTS[command] || '';
	}

	// --- Schede che compaiono da sole: Tabella, Immagine, Grafico, Forma (proposta 2) ---------------------------------

	var CONTEXT_TABS = {
		tabella: { id: 'tabella', label: t('Tabella', 'Table'), context: true, groups: [
			g('Righe e colonne', 'Rows and columns', [ul('Riga sopra', 'Row above', '.uno:InsertRowsBefore'), ul('Riga sotto', 'Row below', '.uno:InsertRowsAfter'),
				ul('Elimina righe', 'Delete rows', '.uno:DeleteRows'), ul('Colonna prima', 'Column before', '.uno:InsertColumnsBefore'),
				ul('Colonna dopo', 'Column after', '.uno:InsertColumnsAfter'), ul('Elimina colonne', 'Delete columns', '.uno:DeleteColumns')]),
			g('Celle', 'Cells', [u('Unisci', 'Merge', '.uno:MergeCells', 'big'), u('Dividi', 'Split', '.uno:SplitCell', 'big', '.uno:TextToColumns'),
				ul('In alto', 'Top', '.uno:CellVertTop'), ul('Al centro', 'Middle', '.uno:CellVertCenter'), ul('In basso', 'Bottom', '.uno:CellVertBottom')]),
			g('Dimensioni', 'Size', [ul('Righe uguali', 'Distribute rows', '.uno:DistributeRows', '.uno:Group'), ul('Colonne uguali', 'Distribute columns', '.uno:DistributeColumns', '.uno:TextToColumns'),
				ul('Seleziona la tabella', 'Select table', '.uno:SelectTable', '.uno:SelectAll')]),
			g('Tabella', 'Table', [u('Proprietà', 'Properties', '.uno:TableDialog', 'big', '.uno:InsertTable'), u('Elimina', 'Delete', '.uno:DeleteTable', 'big', '.uno:DeleteRows')])] },
		immagine: { id: 'immagine', label: t('Immagine', 'Image'), context: true, groups: [
			g('Sistema', 'Adjust', [u('Ritaglia', 'Crop', '.uno:Crop', 'big', '.uno:EuriseRitaglia'), ul('Rifletti in orizzontale', 'Flip horizontally', '.uno:FlipHorizontal', '.uno:EuriseRifletti'),
				ul('Rifletti in verticale', 'Flip vertically', '.uno:FlipVertical', '.uno:EuriseRifletti'), ul('Posizione e dimensioni', 'Position and size', '.uno:TransformDialog')]),
			g('Disponi', 'Arrange', [um(u('A capo', 'Wrap', null, 'big', '.uno:EuriseWrap'), WRAP_MENU), ul('In primo piano', 'Bring to front', '.uno:BringToFront'),
				ul('Sullo sfondo', 'Send to back', '.uno:SendToBack'), ul('Raggruppa', 'Group', '.uno:FormatGroup')]),
			g('Descrivi', 'Describe', [u('Didascalia', 'Caption', '.uno:InsertCaptionDialog', 'big'), ul('Testo alternativo', 'Alt text', '.uno:ObjectTitleDescription', '.uno:AccessibilityCheck'),
				ul('Proprietà', 'Properties', '.uno:GraphicDialog', '.uno:TransformDialog')])] },
		grafico: { id: 'grafico', label: t('Grafico', 'Chart'), context: true, groups: [
			g('Grafico', 'Chart', [u('Tipo', 'Type', '.uno:DiagramType', 'big', '.uno:InsertObjectChart'), u('Dati', 'Data', '.uno:DiagramData', 'big', '.uno:EuriseHighlight'),
				ul('Intervalli dei dati', 'Data ranges', '.uno:DataRanges', '.uno:DefinePrintArea')]),
			g('Elementi', 'Elements', [ul('Titoli', 'Titles', '.uno:InsertMenuTitles', '.uno:InsertCaptionDialog'), ul('Legenda', 'Legend', '.uno:Legend', '.uno:InsertCaptionDialog'),
				ul('Formato della selezione', 'Format selection', '.uno:FormatSelection', '.uno:TransformDialog')])] },
		forma: { id: 'forma', label: t('Forma', 'Shape'), context: true, groups: [
			g('Stile', 'Style', [u('Riempimento', 'Fill', '.uno:FormatArea', 'big', '.uno:EurisePageColor'), u('Linea', 'Line', '.uno:FormatLine', 'big', '.uno:EuriseLinea'),
				ul('Posizione e dimensioni', 'Position and size', '.uno:TransformDialog')]),
			g('Disponi', 'Arrange', [ul('In primo piano', 'Bring to front', '.uno:BringToFront'), ul('Sullo sfondo', 'Send to back', '.uno:SendToBack'),
				ul('Raggruppa', 'Group', '.uno:FormatGroup'), ul('Separa', 'Ungroup', '.uno:FormatUngroup'),
				ul('Rifletti in orizzontale', 'Flip horizontally', '.uno:FlipHorizontal', '.uno:EuriseRifletti'), ul('Rifletti in verticale', 'Flip vertically', '.uno:FlipVertical', '.uno:EuriseRifletti')])] }
	};

	var contextual = { tab: null, back: null };

	function contextKey(contexts) {
		var has = function (re) { return contexts.some(function (c) { return re.test(String(c)); }); };
		if (has(/^Table$/)) return 'tabella';
		if (has(/^Chart|^OLE$/)) return 'grafico';
		if (has(/^Graphic$|^Media$/)) return 'immagine';
		if (has(/^(Draw|DrawText|DrawLine|3DObject|Frame)$/)) return 'forma';
		return null;
	}

	function onContextChange(e) {
		if (!onDesk() || !ribbon.el) return;
		var detail = e && (e.detail || e);
		var contexts = detail.contexts || (detail.context ? [detail.context] : []);
		var key = contextKey(contexts);
		if (key === contextual.tab) return;
		var tabs = document.getElementById('eurise-tabs');
		var old = tabs && tabs.querySelector('.eurise-rtab.context');
		if (old) old.remove();
		var wasOn = contextual.tab && ribbon.tab === contextual.tab;
		contextual.tab = key;
		if (!key) {
			if (wasOn && contextual.back) showRibbon(contextual.back);
			return;
		}
		var tab = CONTEXT_TABS[key];
		var b = document.createElement('button');
		b.type = 'button';
		b.setAttribute('role', 'tab');
		b.setAttribute('data-id', tab.id);
		b.className = 'eurise-rtab context';
		b.innerHTML = '<i></i><span></span>';
		b.lastChild.textContent = tab.label;
		b.onclick = function () { showRibbon(tab); };
		var suite = tabs.querySelector('.eurise-rtab.suite');
		tabs.insertBefore(b, suite);
		if (!wasOn) contextual.back = (ribbon.list || []).filter(function (x) { return x.id === ribbon.tab; })[0] || null;
		showRibbon(tab);
	}

	// --- Barra rapida sulla selezione (proposta 1) -----------------------------------------------------------------

	var mini = { el: null, x: 0, y: 0, at: 0 };

	function hideMini() { if (mini.el) { mini.el.remove(); mini.el = null; } }

	function showMini() {
		hideMini();
		var type = app.map.getDocType();
		if (type !== 'text' && type !== 'presentation') return;
		var bar = document.createElement('div');
		bar.id = 'eurise-mini';
		bar.setAttribute('role', 'toolbar');
		bar.setAttribute('aria-label', t('Barra rapida', 'Quick bar'));
		var add = function (item, suite) {
			var b = ribbonButton(item, suite);
			b.classList.remove('big', 'lab');
			var label = b.querySelector('span');
			if (label && !suite) label.remove();
			bar.appendChild(b);
		};
		var styleMenu = um(u('Stile', 'Style', null, false, '.uno:EditStyle'), [[t('Normale', 'Normal'), style('Standard')], [t('Titolo', 'Title'), style('Title')],
			[t('Titolo 1', 'Heading 1'), style('Heading 1')], [t('Titolo 2', 'Heading 2'), style('Heading 2')], [t('Citazione', 'Quotation'), style('Quotations')]]);
		[u('Grassetto', 'Bold', '.uno:Bold'), u('Corsivo', 'Italic', '.uno:Italic'), u('Sottolineato', 'Underline', '.uno:Underline'),
			colorItem('Evidenzia', 'Highlight', 'back', '.uno:CharBackColor'), colorItem('Colore del carattere', 'Font color', 'font', '.uno:Color'),
			type === 'text' ? styleMenu : null, u('Collegamento', 'Link', fn('hyperlinkdialog'), false, '.uno:HyperlinkDialog'),
			u('Commento', 'Comment', fn('insertcomment'), false, '.uno:InsertAnnotation')].forEach(function (item) { if (item) add(item); });
		var sep = document.createElement('i');
		sep.className = 'eurise-mini-sep';
		bar.appendChild(sep);
		SUITE.items.forEach(function (item) {
			if (item.label === t('Da fare', 'To-do') || item.label === t('Traduci', 'Translate')) add(item, true);
		});
		document.body.appendChild(bar);
		var w = bar.offsetWidth, h = bar.offsetHeight;
		var x = Math.min(Math.max(8, mini.x - w / 2), window.innerWidth - w - 8);
		var y = mini.y - h - 14;
		if (y < 120) y = mini.y + 22;
		bar.style.left = x + 'px';
		bar.style.top = y + 'px';
		mini.el = bar;
	}

	/** Selezione di testo nel documento: la dice l'evento textselectionchange della 26.04. */
	var selectionOn = false;
	function textSelected() { return selectionOn; }

	function watchSelection() {
		document.addEventListener('mouseup', function (e) {
			if (!e.target.closest || !e.target.closest('#document-container, #map')) return;
			mini.x = e.clientX;
			mini.y = e.clientY;
			mini.at = Date.now();
			// Il motore conferma la selezione poco dopo il rilascio del tasto.
			setTimeout(function () {
				var active = textSelected();
				if (active && Date.now() - mini.at < 1500) showMini();
			}, 250);
		}, true);
		document.addEventListener('mousedown', function (e) {
			if (mini.el && !(e.target.closest && e.target.closest('#eurise-mini, #eurise-pop, .eurise-sub'))) hideMini();
		}, true);
		document.addEventListener('keydown', function (e) { if (mini.el && e.key !== 'Shift') hideMini(); }, true);
		document.addEventListener('contextmenu', hideMini, true);
		document.addEventListener('wheel', hideMini, { passive: true, capture: true });
		app.map.on('textselectionchange', function (e) {
			selectionOn = !!e.active;
			if (!e.active) hideMini();
			else if (Date.now() - mini.at < 1500 && !mini.el) showMini();
		});
	}

	// --- Tasto destro: le azioni più probabili, poi «Tutte le voci» (proposta 3) ----------------------------------------

	var lastPointer = { x: 200, y: 200 };
	document.addEventListener('contextmenu', function (e) { lastPointer = { x: e.clientX, y: e.clientY }; }, true);

	var CTX_FIRST = ['.uno:InsertAnnotation', '.uno:EditHyperlink', '.uno:HyperlinkDialog', '.uno:OpenHyperlinkOnCursor', '.uno:InsertRowsAfter',
		'.uno:InsertColumnsAfter', '.uno:DeleteRows', '.uno:DeleteColumns', '.uno:MergeCells', '.uno:TableDialog', '.uno:Crop', '.uno:InsertCaptionDialog',
		'.uno:GraphicDialog', '.uno:TransformDialog', '.uno:FormatArea', '.uno:FormatCellDialog', '.uno:InsertCell', '.uno:DeleteCell',
		'.uno:SetOptimalColumnWidth', '.uno:SortAscending', '.uno:FontDialog', '.uno:ParagraphDialog', '.uno:SetDefault', '.uno:ResetAttributes',
		'.uno:EditStyle', '.uno:AcceptTrackedChange', '.uno:RejectTrackedChange'];

	function flatMenu(menu, out) {
		(menu || []).forEach(function (m) {
			if (!m) return;
			if (m.command && m.type !== 'separator' && m.enabled !== 'false' && m.enabled !== false) out.push(m);
			if (m.menu) flatMenu(m.menu, out);
		});
		return out;
	}

	function wrapContextMenu(tries) {
		var ctrl = app.map && app.map._contextMenu;
		if (!ctrl || typeof ctrl._onContextMenu !== 'function') {
			if (tries > 0) setTimeout(function () { wrapContextMenu(tries - 1); }, 300);
			return;
		}
		if (ctrl._onContextMenu.eurise) return;
		var original = ctrl._onContextMenu;
		var ours = function (obj) {
			var self = this;
			var all = flatMenu(obj && obj.menu, []);
			var spelling = all.some(function (m) { return /^\.uno:SpellCheck(ApplySuggestion|IgnoreAll|Ignore)/.test(m.command); });
			// Da tastiera, sui suggerimenti di ortografia o senza voci: quello di Collabora com'è.
			if (!onDesk() || !obj || obj.mouse === 'false' || spelling || !all.length) return original.call(self, obj);
			var at = lastPointer;
			var pop = popover({ getBoundingClientRect: function () { return { left: at.x, right: at.x, top: at.y, bottom: at.y }; } }, 'ctx');
			var row = document.createElement('div');
			row.className = 'eurise-pctx';
			[['.uno:Cut', t('Taglia', 'Cut')], ['.uno:Copy', t('Copia', 'Copy')], ['.uno:Paste', t('Incolla', 'Paste')]].forEach(function (c) {
				var b = document.createElement('button');
				b.type = 'button';
				b.className = 'eurise-pnb';
				var img = commandIcon(c[0], 18);
				if (img) b.appendChild(img);
				b.appendChild(document.createElement('span')).textContent = c[1];
				b.onclick = function () { closePop(); uno(c[0]); };
				row.appendChild(b);
			});
			pop.appendChild(row);
			var picked = [];
			CTX_FIRST.forEach(function (cmd) {
				all.forEach(function (m) { if (picked.length < 7 && m.command.split('?')[0] === cmd && picked.indexOf(m) < 0) picked.push(m); });
			});
			all.forEach(function (m) {
				if (picked.length < 5 && picked.indexOf(m) < 0 && !/^\.uno:(Cut|Copy|Paste)$/.test(m.command)) picked.push(m);
			});
			picked.forEach(function (m) {
				var label = String(m.text || '').replace(/~/g, '').trim();
				if (!label) return;
				var item = popItem(pop, label, function () { uno(m.command); });
				var img = commandIcon(m.command, 17);
				if (img) item.insertBefore(img, item.firstChild);
			});
			popSection(pop, 'Suite');
			SUITE.items.forEach(function (it) {
				if (it.label === t('Da fare', 'To-do') || it.label === t('Note', 'Notes') || it.label === t('Traduci', 'Translate')) {
					var b = popItem(pop, it.label, it.run, { svg: it.svg });
					b.classList.add('suite');
				}
			});
			popItem(pop, t('Tutte le voci…', 'All items…'), function () { original.call(self, obj); }, { svg: 'M5 12h.01 M12 12h.01 M19 12h.01' });
			pop.place();
		};
		ours.eurise = true;
		// Il controllo ha già agganciato la sua funzione all'evento: si toglie quella e si mette la nostra.
		app.map.off('locontextmenu', original, ctrl);
		ctrl._onContextMenu = ours;
		app.map.on('locontextmenu', ours, ctrl);
		trace.push('context menu');
	}

	// --- Tasto Alt: una lettera su ogni scheda e pulsante (proposta 4) -------------------------------------------------

	var alt = { mode: null, alone: false, map: {} };

	function clearTips() {
		Array.prototype.forEach.call(document.querySelectorAll('.eurise-kt'), function (el) { el.remove(); });
		alt.mode = null;
		alt.map = {};
	}

	function assignTips(buttons, labelOf) {
		var used = {};
		var out = {};
		var spare = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789'.split('');
		buttons.forEach(function (b) {
			var letters = fold(labelOf(b)).toUpperCase().replace(/[^A-Z0-9]/g, '').split('');
			var key = letters.filter(function (c) { return !used[c]; })[0] || spare.filter(function (c) { return !used[c]; })[0];
			if (!key) return;
			used[key] = true;
			out[key] = b;
			var tip = document.createElement('span');
			tip.className = 'eurise-kt';
			tip.textContent = key;
			b.appendChild(tip);
		});
		return out;
	}

	function showTabTips() {
		clearTips();
		var buttons = Array.prototype.slice.call(document.querySelectorAll('#eurise-tabs .eurise-rtab'));
		alt.map = assignTips(buttons, function (b) { return b.textContent; });
		alt.mode = 'tabs';
	}

	function showButtonTips() {
		clearTips();
		var buttons = Array.prototype.slice.call(document.querySelectorAll('#eurise-ribbon .eurise-rb, #eurise-ribbon .eurise-drop, #eurise-ribbon .eurise-gmore'));
		alt.map = assignTips(buttons, function (b) { return b.getAttribute('aria-label') || b.title || b.textContent; });
		alt.mode = 'buttons';
	}

	function watchAlt() {
		document.addEventListener('keydown', function (e) {
			if (e.key === 'Alt' && !e.ctrlKey && !e.shiftKey && !e.metaKey) { alt.alone = true; return; }
			alt.alone = false;
			if (!alt.mode) return;
			if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); clearTips(); return; }
			var key = fold(e.key || '').toUpperCase();
			var target = alt.map[key];
			e.preventDefault();
			e.stopPropagation();
			if (!target) { clearTips(); return; }
			var wasTabs = alt.mode === 'tabs';
			clearTips();
			target.click();
			if (wasTabs && target.getAttribute('data-id')) setTimeout(showButtonTips, 50);
		}, true);
		document.addEventListener('keyup', function (e) {
			if (e.key !== 'Alt' || !alt.alone) return;
			alt.alone = false;
			e.preventDefault();
			if (alt.mode) clearTips(); else showTabTips();
		}, true);
		document.addEventListener('mousedown', function () { if (alt.mode) clearTips(); }, true);
	}

	// --- Nastro a scomparsa e Concentrazione (proposta 9) --------------------------------------------------------------

	function refit() { setTimeout(function () { window.dispatchEvent(new Event('resize')); if (app.map.invalidateSize) app.map.invalidateSize(); }, 60); }

	function setCollapsed(on) {
		document.body.classList.toggle('eurise-nastro-chiuso', on);
		document.body.classList.remove('eurise-nastro-aperto');
		try { window.localStorage.setItem('eurise-nastro-chiuso', on ? '1' : ''); } catch (e) { /* niente memoria */ }
		refit();
	}

	/** Con il nastro chiuso, una scheda lo apre sopra il documento finché non si usa un comando o si clicca fuori. */
	function peek() {
		if (document.body.classList.contains('eurise-nastro-chiuso')) document.body.classList.add('eurise-nastro-aperto');
	}
	function peekDone() { document.body.classList.remove('eurise-nastro-aperto'); }

	function setFocusMode(on) {
		document.body.classList.toggle('eurise-concentrazione', on);
		var pill = document.getElementById('eurise-esci');
		if (on && !pill) {
			pill = document.createElement('button');
			pill.type = 'button';
			pill.id = 'eurise-esci';
			pill.textContent = t('Esci dalla concentrazione · Esc', 'Leave focus mode · Esc');
			pill.onclick = function () { setFocusMode(false); };
			document.body.appendChild(pill);
		} else if (!on && pill) {
			pill.remove();
		}
		refit();
	}

	function watchRibbonToggle() {
		var tabs = document.getElementById('eurise-tabs');
		if (!tabs) return;
		tabs.addEventListener('dblclick', function (e) {
			if (e.target.closest('.eurise-rtab[data-id]')) setCollapsed(!document.body.classList.contains('eurise-nastro-chiuso'));
		});
		tabs.addEventListener('click', function (e) { if (e.target.closest('.eurise-rtab[data-id]')) peek(); });
		document.addEventListener('mousedown', function (e) {
			if (document.body.classList.contains('eurise-nastro-aperto') && !(e.target.closest && e.target.closest('#eurise-top, #eurise-pop, .eurise-sub'))) peekDone();
		}, true);
		document.addEventListener('keydown', function (e) {
			if (e.key === 'Escape' && document.body.classList.contains('eurise-concentrazione') && !document.getElementById('eurise-pop') &&
				!document.querySelector('.jsdialog-window')) setFocusMode(false);
		}, true);
		var saved = '';
		try { saved = window.localStorage.getItem('eurise-nastro-chiuso') || ''; } catch (e) { saved = ''; }
		if (saved) setCollapsed(true);
	}

	/** Messe in piedi sul computer quando il documento ha il suo nastro. */
	function deskErgonomics(list) {
		ribbon.list = list;
		indexDeskItems(list.concat(Object.keys(CONTEXT_TABS).map(function (k) { return CONTEXT_TABS[k]; })));
		app.events.on('contextchange', onContextChange);
		watchSelection();
		wrapContextMenu(40);
		watchAlt();
		watchRibbonToggle();
	}


	// --- Testi di Collabora rimasti in inglese nelle finestre e nella colonna (tavola OffPCFinestre) ---------------------

	var IT_FIX = {
		'Find': 'Trova', 'Replace': 'Sostituisci', 'Find All': 'Trova tutto', 'Replace All': 'Sostituisci tutto', 'Find Next': 'Trova successivo',
		'Find Previous': 'Trova precedente', 'Match case': 'Maiuscole e minuscole', 'Whole words only': 'Solo parole intere', 'Other options': 'Altre opzioni',
		'Highlight styles': 'Evidenzia gli stili', 'Filter': 'Filtro', 'Show previews': 'Mostra le anteprime', 'Hierarchical': 'Gerarchico',
		'New': 'Nuovo', 'Close': 'Chiudi', 'Cancel': 'Annulla', 'Apply': 'Applica', 'Help': 'Aiuto', 'Reset': 'Ripristina',
		'Untitled': 'Senza titolo', 'Rename': 'Rinomina', 'Save': 'Salva', 'Insert': 'Inserisci', 'Delete': 'Elimina', 'Search': 'Cerca'
	};

	var FIX_SCOPE = '.jsdialog-window, .jsdialog-container, .ui-dialog, #sidebar-dock-wrapper, #eurise-start';

	function fixTexts(root) {
		if (!IT || !root || root.nodeType !== 1) return;
		var inside = root.closest(FIX_SCOPE) || root.querySelector(FIX_SCOPE);
		if (!inside) return;
		var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
		var node;
		while ((node = walker.nextNode())) {
			var text = node.nodeValue.trim();
			if (text && IT_FIX[text] && node.parentNode && node.parentNode.closest(FIX_SCOPE)) node.nodeValue = node.nodeValue.replace(text, IT_FIX[text]);
		}
	}

	function watchTexts() {
		if (!IT || watchTexts.on) return;
		watchTexts.on = true;
		new MutationObserver(function (records) {
			records.forEach(function (rec) {
				Array.prototype.forEach.call(rec.addedNodes, function (n) { if (n.nodeType === 1) fixTexts(n); });
			});
			records.forEach(function (rec) {
				if (rec.type === 'characterData' && rec.target.parentNode) fixTexts(rec.target.parentNode);
			});
		}).observe(document.body, { childList: true, subtree: true, characterData: true });
	}

	// --- Inizio Eurise: pagina iniziale del guscio Linux (tavola OffPCInizio) ---------------------------------------------

	var KIND_COLOR = { doc: '#3E5C8A', sheet: '#3F7A55', slide: '#A5532F' };

	function startPage() {
		if (document.getElementById('eurise-start')) return;
		document.body.classList.add('eurise-desk', 'eurise-starter');
		watchTexts();
		var page = document.createElement('div');
		page.id = 'eurise-start';
		var hour = new Date().getHours();
		var hello = hour < 13 ? t('Buongiorno', 'Good morning') : hour < 18 ? t('Buon pomeriggio', 'Good afternoon') : t('Buonasera', 'Good evening');
		page.innerHTML =
			'<div class="es-main">' +
			'<div class="es-head"><h1></h1><span class="es-sub"></span><span class="es-fill"></span>' +
			'<label class="es-search">' + icon(SVG.search, 16) + '<input type="search"></label></div>' +
			'<div class="es-new"></div>' +
			'<div class="es-title"><h2></h2><span class="es-sub es-tsub"></span><span class="es-fill"></span><button type="button" class="es-all"></button></div>' +
			'<div class="es-templates"></div>' +
			'<div class="es-title"><h2></h2><span class="es-sub es-rsub"></span></div>' +
			'<div class="es-recent"></div></div>' +
			'<aside class="es-side"><span class="es-label"></span><div class="es-open"></div><span class="es-label es-flabel"></span><div class="es-folders"></div></aside>';
		page.querySelector('h1').textContent = hello;
		page.querySelector('.es-head .es-sub').textContent = t('cosa vuoi creare?', 'what would you like to create?');
		var search = page.querySelector('.es-search input');
		search.placeholder = t('Cerca tra modelli e documenti recenti', 'Search templates and recent documents');
		search.setAttribute('aria-label', search.placeholder);
		var titles = page.querySelectorAll('.es-title h2');
		titles[0].textContent = t('Modelli', 'Templates');
		titles[1].textContent = t('Recenti', 'Recent');
		page.querySelector('.es-tsub').textContent = t('per lettere, conti, riunioni…', 'for letters, budgets, meetings…');
		page.querySelector('.es-rsub').textContent = t('gli ultimi documenti aperti', 'the last documents you opened');
		var allBtn = page.querySelector('.es-all');
		allBtn.textContent = t('Tutti i modelli', 'All templates');
		page.querySelector('.es-label').textContent = t('Apri', 'Open');
		page.querySelector('.es-flabel').textContent = t('Cartelle recenti', 'Recent folders');

		// Nuovo: Documento, Foglio, Presentazione.
		var news = page.querySelector('.es-new');
		[['doc', 'writer', t('Documento', 'Document'), t('Lettere, relazioni, curriculum', 'Letters, reports, CVs')],
			['sheet', 'calc', t('Foglio', 'Spreadsheet'), t('Conti, elenchi, calcoli', 'Budgets, lists, calculations')],
			['slide', 'impress', t('Presentazione', 'Presentation'), t('Diapositive per le riunioni', 'Slides for meetings')]].forEach(function (n) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'es-card ' + n[0];
			b.innerHTML = '<span class="es-pic">' + icon(SVGD[n[0]], 46) + '</span><span class="es-name"><i></i><b></b></span><span class="es-desc"></span>';
			b.querySelector('b').textContent = n[2];
			b.querySelector('.es-desc').textContent = n[3];
			b.onclick = function () { newFrom(n[1]); };
			news.appendChild(b);
		});

		// Apri.
		var open = page.querySelector('.es-open');
		[[t('Apri da Eurise File…', 'Open from Eurise File…'), SVGD.folder], [t('Dal computer…', 'From this computer…'), SVGD.pc]].forEach(function (o) {
			var b = document.createElement('button');
			b.type = 'button';
			b.className = 'es-openbtn';
			b.innerHTML = icon(o[1], 19) + '<span></span>';
			b.lastChild.textContent = o[0];
			b.onclick = function () { if (window.postMobileMessage) window.postMobileMessage('uno .uno:Open'); };
			open.appendChild(b);
		});

		// Modelli: quelli del pacchetto (templates/templates.js), documento, foglio e presentazione insieme.
		var templates = (window.CODA_TEMPLATES || []).filter(function (e) { return e && e.path && e.name && /writer|calc|impress/.test(templateType(e)); });
		var showAll = false;
		var tbox = page.querySelector('.es-templates');
		var rbox = page.querySelector('.es-recent');
		var recents = [];
		function renderTemplates() {
			var q = fold(search.value.trim());
			var list = templates.filter(function (e) { return !q || fold(tr(e.name)).indexOf(q) >= 0; });
			if (!showAll && !q) {
				// Due per tipo, i primi della lista.
				var per = { writer: 0, calc: 0, impress: 0 };
				list = list.filter(function (e) { var k = templateType(e); per[k] = (per[k] || 0) + 1; return per[k] <= 2; });
			}
			tbox.innerHTML = '';
			tbox.classList.toggle('all', showAll || !!q);
			list.forEach(function (e) {
				var kind = { writer: 'doc', calc: 'sheet', impress: 'slide' }[templateType(e)];
				var b = document.createElement('button');
				b.type = 'button';
				b.className = 'es-tpl ' + kind;
				b.innerHTML = '<span class="es-thumb"></span><b></b><small></small>';
				if (e.preview) {
					var img = document.createElement('img');
					img.alt = '';
					img.src = String(e.preview).indexOf('/') === 0 || /^[a-z]+:/i.test(e.preview) ? e.preview : 'templates/' + e.preview;
					img.onerror = function () { img.remove(); b.querySelector('.es-thumb').innerHTML = '<i></i><i></i><i></i>' + icon(SVGD[kind], 14); };
					b.firstChild.appendChild(img);
				} else {
					b.firstChild.innerHTML = '<i></i><i></i><i></i>' + icon(SVGD[kind], 14);
				}
				b.querySelector('b').textContent = tr(e.name);
				b.querySelector('small').textContent = { doc: t('Documento', 'Document'), sheet: t('Foglio', 'Spreadsheet'), slide: t('Presentazione', 'Presentation') }[kind];
				b.onclick = function () { newFrom(templateType(e), e); };
				tbox.appendChild(b);
			});
			allBtn.style.display = q ? 'none' : '';
			allBtn.textContent = showAll ? t('Meno modelli', 'Fewer templates') : t('Tutti i modelli', 'All templates');
		}
		function renderRecent() {
			var q = fold(search.value.trim());
			rbox.innerHTML = '';
			var list = recents.filter(function (d) { return !q || fold(d.name + ' ' + d.folder).indexOf(q) >= 0; });
			if (!list.length) {
				var p = document.createElement('p');
				p.className = 'es-empty';
				p.textContent = q ? t('Nessun documento con questo nome.', 'No document with this name.') : t('I documenti che apri compaiono qui.', 'Documents you open appear here.');
				rbox.appendChild(p);
			}
			list.slice(0, 12).forEach(function (d) {
				var b = document.createElement('button');
				b.type = 'button';
				b.className = 'es-row';
				b.innerHTML = icon(SVGD[d.kind], 20) + '<b></b><span class="es-where"></span><span class="es-when"></span>';
				b.firstChild.style.color = KIND_COLOR[d.kind];
				b.querySelector('b').textContent = d.name;
				b.querySelector('.es-where').textContent = d.folder;
				b.querySelector('.es-when').textContent = when(d.when);
				b.onclick = function () { openRecent(d.uri); };
				rbox.appendChild(b);
			});
			var folders = page.querySelector('.es-folders');
			folders.innerHTML = '';
			var seen = {};
			recents.forEach(function (d) {
				if (!d.folder || seen[d.folder] || Object.keys(seen).length >= 4) return;
				seen[d.folder] = true;
				var row = document.createElement('div');
				row.className = 'es-folder';
				row.innerHTML = icon(SVGD.folder, 17) + '<span></span>';
				row.lastChild.textContent = d.folder;
				folders.appendChild(row);
			});
			page.querySelector('.es-flabel').style.display = Object.keys(seen).length ? '' : 'none';
		}
		allBtn.onclick = function () { showAll = !showAll; renderTemplates(); };
		search.oninput = function () { renderTemplates(); renderRecent(); };
		renderTemplates();
		renderRecent();
		var loadRecent = function () { recentDocs(function (docs) { recents = docs.map(recentInfo); renderRecent(); }); };
		if (!window.qtBridgeReady) window.addEventListener('qtbridgeready', loadRecent, { once: true });
		else loadRecent();
		document.body.appendChild(page);
		setTimeout(function () { search.focus(); }, 300);
	}

	// Nomi dei modelli di Collabora che la pagina iniziale lascia in inglese.
	var TEMPLATE_IT = {
		'Business Letter': 'Lettera commerciale', 'Classic Business Letter': 'Lettera commerciale classica',
		'Curriculum Vitae(CV)': 'Curriculum vitae', 'Curriculum Vitae (CV)': 'Curriculum vitae', 'Resume (one-page)': 'Curriculum in una pagina',
		'Modern': 'Moderno', 'Default': 'Predefinito', 'Blank Document': 'Documento vuoto', 'Blank Spreadsheet': 'Foglio vuoto',
		'Blank Presentation': 'Presentazione vuota', 'Untitled': 'Senza titolo'
	};

	function tr(text) {
		var out = typeof window._ === 'function' ? window._(text) : text;
		return IT && out === text && TEMPLATE_IT[text] ? TEMPLATE_IT[text] : out;
	}

	function templateType(e) {
		var type = String(e.type || '').toLowerCase();
		if (/calc|spreadsheet|ots|ods/.test(type) || /\.ot[s]$/i.test(e.path)) return 'calc';
		if (/impress|presentation|otp|odp/.test(type) || /\.otp$/i.test(e.path)) return 'impress';
		if (/draw|otg/.test(type)) return 'draw';
		return 'writer';
	}

	function newFrom(type, template) {
		var params = ['type=' + type];
		if (template && template.path) params.push('template=' + encodeURIComponent(template.path));
		params.push('basename=' + encodeURIComponent(template && template.basename ? tr(template.basename) : t('Senza titolo', 'Untitled')));
		if (window.postMobileMessage) window.postMobileMessage('newdoc ' + params.join(' '));
	}

	function when(stamp) {
		if (!stamp) return '';
		var d = new Date(stamp);
		if (isNaN(d.getTime())) return '';
		var now = new Date();
		var day = 86400000;
		var start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
		if (d.getTime() >= start) return t('oggi, ', 'today, ') + d.toLocaleTimeString(IT ? 'it-IT' : undefined, { hour: '2-digit', minute: '2-digit' });
		if (d.getTime() >= start - day) return t('ieri', 'yesterday');
		return d.toLocaleDateString(IT ? 'it-IT' : undefined, { day: 'numeric', month: 'short' });
	}

	(function waitForStarter(tries) {
		var starter = false;
		try { starter = !!new URLSearchParams(window.location.search).get('starterMode'); } catch (e) { starter = false; }
		if (!starter || !window.ThisIsTheQtApp) {
			if (!window.ThisIsTheQtApp && tries > 0) setTimeout(function () { waitForStarter(tries - 1); }, 100);
			return;
		}
		if (!document.body) { setTimeout(function () { waitForStarter(tries - 1); }, 50); return; }
		startPage();
	})(100);


	function tabletChrome(docType) {
		var row = document.getElementById('toolbar-row');
		trace.push('chrome row=' + !!row);
		if (!row || document.getElementById('eurise-top')) return;
		var top = document.createElement('div');
		top.id = 'eurise-top';
		var desk = onDesk();
		// Tablet e computer (richiesta del proprietario, 14 novembre): una riga sola. A sinistra File e le schede; a destra
		// la pillola chiara con il nome, lo stato del salvataggio e annulla/ripeti, poi «Cerca un comando» più corto e
		// Condividi con la sola icona. Niente più riga del titolo con lo spazio vuoto.
		var pill =
			'<div class="eurise-pill"><span class="eurise-doc"><span class="eurise-name"></span><span class="eurise-status"></span></span>' +
			'<button type="button" class="eurise-hbtn" data-a="undo" aria-label="' + t('Annulla', 'Undo') + '" title="' + t('Annulla', 'Undo') + '">' + icon(SVG.undo, 18) + '</button>' +
			'<button type="button" class="eurise-hbtn" data-a="redo" aria-label="' + t('Ripeti', 'Redo') + '" title="' + t('Ripeti', 'Redo') + '">' + icon('M15 6l5 5-5 5 M20 11H10a6 6 0 0 0 0 12', 18) + '</button></div>';
		var tools =
			'<button type="button" class="eurise-cmdsearch" data-a="search" title="' + t('Cerca un comando', 'Search a command') + ' (Ctrl K)">' + icon(SVG.search, 16) +
			'<span>' + t('Cerca un comando', 'Search a command') + '</span><kbd>Ctrl K</kbd></button>' +
			'<button type="button" class="eurise-share icon" data-a="share" aria-label="' + t('Condividi', 'Share') + '" title="' + t('Condividi', 'Share') + '">' + icon(SVG.share, 17) + '</button>';
		top.innerHTML = '<div id="eurise-header" class="riga' + (desk ? ' desk' : '') + '"><div id="eurise-tabs" role="tablist"></div>' +
			'<span class="eurise-fill"></span>' + pill + tools + '</div><div id="eurise-ribbon"></div>';
		top.querySelector('#eurise-header').onclick = function (e) {
			var b = e.target.closest && e.target.closest('button');
			if (!b) return;
			var a = b.getAttribute('data-a');
			if (!a) return;
			if (a === 'undo') uno('.uno:Undo');
			else if (a === 'redo') uno('.uno:Redo');
			else if (a === 'search') openSearch();
			else if (a === 'share') beforeSharing();
		};
		var tabs = top.querySelector('#eurise-tabs');
		var file = document.createElement('button');
		file.type = 'button';
		file.className = 'eurise-rtab file';
		file.textContent = t('File', 'File');
		file.onclick = function () { anchorEl = file; fileMenu(); };
		tabs.appendChild(file);
		// Sul computer il nastro completo (tavola OffPCNastro); sul tablet quello compatto.
		var list = (desk && DESK_RIBBONS[docType]) || RIBBONS[docType] || RIBBONS.text;
		list.forEach(function (tab) {
			var b = document.createElement('button');
			b.type = 'button';
			b.setAttribute('role', 'tab');
			b.setAttribute('data-id', tab.id);
			b.className = 'eurise-rtab' + (tab.suite ? ' suite' : '');
			b.innerHTML = (tab.suite ? '<svg width="11" height="11" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="' + SVG.star + '"></path></svg>' : '') + '<span>' + tab.label + '</span>';
			b.onclick = function () { showRibbon(tab); };
			tabs.appendChild(b);
		});
		row.appendChild(top);
		ribbon.el = top.querySelector('#eurise-ribbon');
		showRibbon(list[0]);
		status();
		if (desk) {
			// Il nome nella pillola si cambia lì: un clic (o F2) e si scrive (tavola OffPCTendine).
			var nameEl = top.querySelector('.eurise-name');
			nameEl.title = t('Rinomina', 'Rename');
			nameEl.onclick = startRename;
			deskErgonomics(list);
			deskSide(docType);
			deskRulers(docType);
			// La 26.04 disegna i righelli al primo evento della pagina: un ridimensionamento li fa comparire subito.
			setTimeout(function () { window.dispatchEvent(new Event('resize')); }, 1500);
		}
		if (app.map.invalidateSize) setTimeout(function () { app.map.invalidateSize(); }, 50);
	}

	// --- Computer: colonna Properties di Collabora con le nostre sezioni (indicazioni del proprietario, 14 novembre) ---
	//
	// La colonna di destra resta quella di Collabora, con tutte le sue funzioni, ridisegnata in eurise.css (isole bianche sul
	// Perla, tendine e pulsanti come il nastro). In cima, come sezioni sue: Struttura (il Navigatore del motore, spostato
	// qui: niente colonna a sinistra), Collegato a e In Eurise File. Nelle presentazioni le diapositive restano a sinistra.

	/** Percorso del documento: nel guscio Linux arriva come file:// nelle opzioni della mappa. */
	function documentPath() {
		var candidates = [app.map && app.map.options && app.map.options.doc, window.wopiSrc, window.docURL];
		try { candidates.push(new URLSearchParams(window.location.search).get('file_path')); } catch (e) { /* niente */ }
		for (var i = 0; i < candidates.length; i++) {
			var c = candidates[i];
			if (typeof c !== 'string' || !c) continue;
			try { c = decodeURIComponent(c); } catch (e) { /* già decodificato */ }
			c = c.replace(/^file:\/\//, '').split('?')[0];
			if (c.indexOf('/') >= 0) return c;
		}
		return '';
	}

	function sideSection(id, title, open) {
		var sec = document.createElement('section');
		sec.className = 'eurise-side' + (open ? ' open' : '');
		sec.id = id;
		var head = document.createElement('button');
		head.type = 'button';
		head.className = 'eurise-side-head';
		head.setAttribute('aria-expanded', open ? 'true' : 'false');
		head.innerHTML = '<span></span>' + DROP;
		head.firstChild.textContent = title;
		head.onclick = function () {
			var now = !sec.classList.contains('open');
			sec.classList.toggle('open', now);
			head.setAttribute('aria-expanded', now ? 'true' : 'false');
		};
		var body = document.createElement('div');
		body.className = 'eurise-side-body';
		sec.appendChild(head);
		sec.appendChild(body);
		return sec;
	}

	function sideRow(main, sub) {
		var row = document.createElement('div');
		row.className = 'eurise-side-row';
		var a = document.createElement('strong');
		a.textContent = main;
		row.appendChild(a);
		if (sub) {
			var b = document.createElement('span');
			b.textContent = sub;
			row.appendChild(b);
		}
		return row;
	}

	function deskSide(docType) {
		var wrapper = document.getElementById('sidebar-dock-wrapper');
		trace.push('side wrapper=' + !!wrapper);
		document.body.classList.add('eurise-doc-' + docType);
		if (!wrapper || document.getElementById('eurise-sidebox')) return;
		var box = document.createElement('div');
		box.id = 'eurise-sidebox';

		// Struttura: il Navigatore del motore (titoli, tabelle, immagini…; i fogli nel foglio di calcolo).
		if (docType !== 'presentation' && docType !== 'drawing') {
			var structure = sideSection('eurise-struttura', t('Struttura', 'Outline'), true);
			var panel = document.getElementById('navigator-panel');
			if (panel) structure.lastChild.appendChild(panel);
			var empty = document.createElement('p');
			empty.className = 'eurise-side-empty';
			empty.textContent = t('Titoli, tabelle e immagini del documento compaiono qui.', 'Headings, tables and images appear here.');
			structure.lastChild.appendChild(empty);
			box.appendChild(structure);
			// Il Navigatore si apre una volta: lo costruisce il motore dentro #navigator-panel, che ora sta qui.
			var opened = false;
			var openNavigator = function () {
				if (opened) return;
				opened = true;
				setTimeout(function () { uno('.uno:Navigator'); }, 300);
			};
			if (app.map._docLoaded) openNavigator(); else app.map.once('docloaded', openNavigator);
			app.map.on('navigator', function () { structure.classList.add('filled'); });
		}

		// Collegato a: eventi, persone e attività della suite legati al documento (arrivano con il guscio Eurise).
		var linked = sideSection('eurise-collegato', t('Collegato a', 'Linked to'), true);
		linked.lastChild.appendChild(sideRow(t('Nessun collegamento', 'Nothing linked yet'),
			t('Collega un evento, una persona o un Da fare dalla scheda Suite.', 'Link an event, a person or a to-do from the Suite tab.')));
		box.appendChild(linked);

		// In Eurise File: dove sta il documento.
		var where = sideSection('eurise-file', t('In Eurise File', 'In Eurise File'), true);
		var path = documentPath();
		var parts = path.split('/').filter(Boolean);
		var folder = parts.length > 1 ? parts.slice(Math.max(0, parts.length - 3), parts.length - 1).join(' › ') : '';
		where.lastChild.appendChild(sideRow(folder || t('Sul computer', 'On this computer'),
			t('Sul computer · si sincronizza con il guscio Eurise', 'On this computer · syncs with the Eurise shell')));
		box.appendChild(where);

		wrapper.insertBefore(box, wrapper.firstChild);
		document.body.classList.add('eurise-side-on');
	}

	/**
	 * Sul computer i righelli (orizzontale e verticale) si vedono sempre nei testi, come in un programma da scrivania
	 * (richiesta del proprietario, 14 novembre). Collabora li crea dopo il caricamento: si aspetta che ci siano.
	 */
	function deskRulers(docType) {
		if (docType !== 'text') return;
		(function tryRulers(tries) {
			var ui = app.map.uiManager;
			var vertical = app.UI && app.UI.verticalRuler;
			if (ui && !vertical && tries === 20 && ui.showRuler) { trace.push('showRuler'); ui.showRuler(); }
			if (!ui || !vertical) {
				if (tries > 0) setTimeout(function () { tryRulers(tries - 1); }, 500);
				return;
			}
			if (ui.isRulerVisible && !ui.isRulerVisible() && ui.showRuler) ui.showRuler();
			if (vertical.show) vertical.show();
			if (app.UI.horizontalRuler && app.UI.horizontalRuler.show) app.UI.horizontalRuler.show();
			app.map.fire('rulerchanged');
			trace.push('rulers on');
		})(40);
	}

	/**
	 * Il giro guidato di Collabora (26.04, «Welcome to Collabora Online… This short tour») non serve: la prima volta che
	 * compare lo si salta con il suo pulsante, così Collabora ricorda di non mostrarlo più.
	 */
	function skipTour() {
		var SKIP = /^(skip|salta|ignora)$/i;
		function check(root) {
			if (!root || !root.querySelectorAll) return;
			var buttons = root.querySelectorAll('button');
			for (var i = 0; i < buttons.length; i++) {
				var b = buttons[i];
				if (!SKIP.test((b.textContent || '').trim())) continue;
				var box = b.closest('[role="dialog"], dialog, .jsdialog-window, .ui-dialog, div[class*="tour"], div[id*="tour"]') || b.parentNode;
				var text = (box && box.textContent || '').toLowerCase();
				if (text.indexOf('tour') >= 0 || text.indexOf('giro') >= 0 || text.indexOf('welcome') >= 0 || text.indexOf('benvenut') >= 0) {
					trace.push('tour skipped');
					b.click();
					return;
				}
			}
		}
		check(document.body);
		new MutationObserver(function (records) {
			records.forEach(function (rec) { Array.prototype.forEach.call(rec.addedNodes, check); });
			records.forEach(function (rec) {
				if (rec.type === 'characterData' && rec.target.parentNode) fixTexts(rec.target.parentNode);
			});
		}).observe(document.body, { childList: true, subtree: true, characterData: true });
		setTimeout(function () { check(document.body); }, 4000);
	}

	function onRibbonState(e) {
		if (!ribbon.el || typeof e.state !== 'string') return;
		Array.prototype.forEach.call(ribbon.el.querySelectorAll('[data-uno="' + e.commandName + '"]'), function (b) {
			b.classList.toggle('on', e.state === 'true');
		});
	}

	function setupTablet() {
		var map = app.map;
		trace.push('setupTablet');
		if (!map || !window.mode) return;
		// Tablet nell'app Android, oppure computer nel guscio Linux di Collabora (coda-qt): stesso nastro (OffTablet,
		// OffComputer); sul computer la classe eurise-desk regola le misure.
		// Il guscio Qt è sempre sul computer (all'avvio window.mode.isDesktop() può non avere ancora la risposta giusta).
		var desk = !!window.ThisIsTheQtApp;
		if (!desk && !(window.mode.isTablet() && window.ThisIsAMobileApp)) return;
		document.body.classList.add('eurise-tablet');
		if (desk) {
			document.body.classList.add('eurise-desk');
			// Il giro guidato della 26.04 parte solo se questa preferenza manca (Control.UIManager, onboardingTourSeen).
			try { map.uiManager.setDocTypePref('onboardingTourSeen', true); } catch (e) { trace.push('tour pref ' + e); }
			// Righelli: Collabora li crea all'apertura solo se la preferenza ShowRuler è accesa.
			try { if (!map.uiManager.getBooleanDocTypePref('ShowRuler', false)) map.uiManager.setDocTypePref('ShowRuler', true); } catch (e) { trace.push('ruler pref ' + e); }
			skipTour();
			watchTexts();
			document.addEventListener('keydown', function (e) {
				if (e.key === 'F2' && !e.ctrlKey && !e.altKey && !e.shiftKey) { e.preventDefault(); startRename(); }
				else if ((e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && (e.key === 'f' || e.key === 'F' || e.key === 'h' || e.key === 'H')) {
					e.preventDefault();
					e.stopPropagation();
					deskFind(e.key === 'h' || e.key === 'H');
				}
			}, true);
		}
		trace.push(desk ? 'desk' : 'tablet');
		editAtOnce(map);
		function build() {
			trace.push('build ' + map.getDocType());
			if (isPdf() && !desk) { document.body.classList.remove('eurise-tablet'); return; }
			// Sul computer anche il PDF ha la nostra testata (si apre in sola lettura nel motore).
			if (isPdf()) document.body.classList.add('eurise-pdf');
			tabletChrome(isPdf() ? 'pdf' : map.getDocType());
		}
		if (map._docLayer) build(); else map.on('doclayerinit', build);
		map.on('docloaded wopiprops', status);
		map.on('commandstatechanged', onState);
		map.on('commandstatechanged', onRibbonState);
	}

	(function waitForTabletMap(tries) {
		if (ready()) { setupTablet(); return; }
		if (tries > 0) setTimeout(function () { waitForTabletMap(tries - 1); }, 100);
	})(300);

	// Accento delle icone all'avvio: sul computer quello scelto (memoria del guscio), sul telefono e sul tablet lo chiede
	// al guscio del motore, che l'ha ricevuto da Office.
	(function startAccent(tries) {
		if (window.ThisIsTheQtApp) {
			try {
				var saved = JSON.parse(window.localStorage.getItem('eurise-accento') || 'null');
				if (saved) setAccent(saved.light, saved.dark);
			} catch (e) { /* memoria non disponibile: resta l'accento di base */ }
			return;
		}
		if (!window.ThisIsAMobileApp) return;
		if (!window.postMobileMessage) {
			if (tries > 0) setTimeout(function () { startAccent(tries - 1); }, 100);
			return;
		}
		toShell({ action: 'aspetto' }, function (r) { if (r && r.light) setAccent(r.light, r.dark); });
	})(100);

	window.euriseOffice = { reply: reply, openSearch: openSearch, notice: notice, trace: trace, missingIcons: missingIcons, setAccent: setAccent };
	if (typeof JSDialog !== 'undefined') JSDialog.EuriseSearch = openSearch;
})();

/* Shoonya Market Movers - dashboard client.
 * Connects to the backend WebSocket, renders index cards and per-interval
 * gainers/losers tables, and auto-reconnects if the socket drops. */
(function () {
    "use strict";

    var activeInterval = null;   // selected tab (seconds)
    var latest = null;           // last snapshot received
    var ws = null;
    var reconnectTimer = null;

    function fmt(n, dp) {
        if (n === null || n === undefined || isNaN(n)) return "-";
        return Number(n).toLocaleString("en-IN", {
            minimumFractionDigits: dp === undefined ? 2 : dp,
            maximumFractionDigits: dp === undefined ? 2 : dp
        });
    }

    function fmtVol(n) {
        if (!n) return "-";
        if (n >= 1e7) return (n / 1e7).toFixed(2) + "Cr";
        if (n >= 1e5) return (n / 1e5).toFixed(2) + "L";
        if (n >= 1e3) return (n / 1e3).toFixed(1) + "K";
        return String(n);
    }

    function signClass(n) {
        if (n > 0) return "pos";
        if (n < 0) return "neg";
        return "";
    }

    function signStr(n, dp) {
        var s = n > 0 ? "+" : "";
        return s + fmt(n, dp);
    }

    function connect() {
        var proto = location.protocol === "https:" ? "wss:" : "ws:";
        var url = proto + "//" + location.host + "/ws/market";
        ws = new WebSocket(url);

        ws.onopen = function () {
            setConn(true);
        };
        ws.onmessage = function (evt) {
            try {
                latest = JSON.parse(evt.data);
                render(latest);
            } catch (e) {
                console.error("bad snapshot", e);
            }
        };
        ws.onclose = function () {
            setConn(false);
            scheduleReconnect();
        };
        ws.onerror = function () {
            try { ws.close(); } catch (e) { /* ignore */ }
        };
    }

    function scheduleReconnect() {
        if (reconnectTimer) return;
        reconnectTimer = setTimeout(function () {
            reconnectTimer = null;
            connect();
        }, 3000);
    }

    function setConn(up) {
        var pill = document.getElementById("connPill");
        if (up) {
            pill.textContent = "socket live";
            pill.className = "pill ok";
        } else {
            pill.textContent = "reconnecting";
            pill.className = "pill down";
        }
    }

    function render(snap) {
        renderHeader(snap);
        renderIndices(snap.indices || []);
        renderTabs(snap.intervals || []);
        renderPanels(snap.intervals || []);

        var last = document.getElementById("lastUpdate");
        last.textContent = "Updated " + new Date(snap.serverTime).toLocaleTimeString("en-IN");
    }

    function renderHeader(snap) {
        var mode = document.getElementById("modePill");
        mode.textContent = snap.marketMode === "LIVE" ? "LIVE FEED" : "MOCK FEED";
        mode.className = "pill " + (snap.marketMode === "LIVE" ? "live" : "mock");

        var s = snap.stats || {};
        document.getElementById("metaStats").textContent =
            (s.trackedScrips || 0) + " scrips \u00b7 " +
            fmtVol(s.totalTicks || 0) + " ticks \u00b7 up " + (s.uptimeSec || 0) + "s";
    }

    function renderIndices(indices) {
        var host = document.getElementById("indices");
        host.innerHTML = "";
        indices.forEach(function (ix) {
            var card = document.createElement("div");
            card.className = "index-card";
            var cls = signClass(ix.pctChange);
            card.innerHTML =
                '<div class="name">' + esc(ix.name) + '</div>' +
                '<div class="ltp">' + (ix.hasData ? fmt(ix.ltp) : "&mdash;") + '</div>' +
                '<div class="chg ' + cls + '">' + signStr(ix.change) + '  (' + signStr(ix.pctChange) + '%)</div>' +
                '<div class="ohlc">' +
                '<span>O<b>' + fmt(ix.open) + '</b></span>' +
                '<span>H<b>' + fmt(ix.high) + '</b></span>' +
                '<span>L<b>' + fmt(ix.low) + '</b></span>' +
                '<span>PC<b>' + fmt(ix.prevClose) + '</b></span>' +
                '</div>';
            host.appendChild(card);
        });
    }

    function renderTabs(intervals) {
        var host = document.getElementById("tabs");
        host.innerHTML = "";
        if (activeInterval === null && intervals.length) {
            activeInterval = intervals[0].seconds;
        }
        intervals.forEach(function (iv) {
            var tab = document.createElement("div");
            tab.className = "tab" + (iv.seconds === activeInterval ? " active" : "");
            tab.innerHTML = esc(iv.label) +
                '<span class="dot ' + (iv.ready ? "ready" : "") + '" title="' +
                (iv.ready ? "window ready" : "collecting history") + '"></span>';
            tab.onclick = function () {
                activeInterval = iv.seconds;
                if (latest) {
                    renderTabs(latest.intervals || []);
                    renderPanels(latest.intervals || []);
                }
            };
            host.appendChild(tab);
        });
    }

    function renderPanels(intervals) {
        var host = document.getElementById("panels");
        host.innerHTML = "";
        intervals.forEach(function (iv) {
            var panel = document.createElement("div");
            panel.className = "panel" + (iv.seconds === activeInterval ? " active" : "");
            var note = iv.ready ? "" :
                '<p class="empty">Collecting price history for the ' + esc(iv.label) +
                ' window&hellip; results appear once enough ticks are gathered.</p>';
            panel.innerHTML =
                '<div class="cols">' +
                '<div class="col"><h2 class="gain">Top Gainers &middot; ' + esc(iv.label) + '</h2>' +
                note + table(iv.gainers, "gain") + '</div>' +
                '<div class="col"><h2 class="lose">Top Losers &middot; ' + esc(iv.label) + '</h2>' +
                note + table(iv.losers, "lose") + '</div>' +
                '</div>';
            host.appendChild(panel);
        });
    }

    function table(rows, kind) {
        if (!rows || !rows.length) {
            return '<p class="empty">No movers in this window yet.</p>';
        }
        var head =
            '<tr>' +
            '<th class="sym">Symbol</th>' +
            '<th>LTP</th>' +
            '<th>&Delta;</th>' +
            '<th>&Delta;%</th>' +
            '<th>Day%</th>' +
            '<th>Vol</th>' +
            '<th class="insight">Insight</th>' +
            '</tr>';
        var body = rows.map(function (r) {
            return '<tr class="' + kind + '">' +
                '<td class="sym">' + esc(r.symbol) + '<span class="meta"> ' + esc(r.exchange) + '</span></td>' +
                '<td>' + fmt(r.ltp) + '</td>' +
                '<td class="' + signClass(r.changeAbs) + '">' + signStr(r.changeAbs) + '</td>' +
                '<td class="pct">' + signStr(r.changePct) + '%</td>' +
                '<td class="' + signClass(r.dayPct) + '">' + signStr(r.dayPct) + '%</td>' +
                '<td>' + fmtVol(r.volume) + '</td>' +
                '<td class="insight">' + esc(r.insight || "") + '</td>' +
                '</tr>';
        }).join("");
        return '<table>' + head + body + '</table>';
    }

    function esc(s) {
        if (s === null || s === undefined) return "";
        return String(s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");
    }

    connect();
})();

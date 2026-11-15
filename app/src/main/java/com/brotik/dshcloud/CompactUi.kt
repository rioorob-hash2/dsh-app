package com.brotik.dshcloud

import android.webkit.WebView

/**
 * Компактный composer: прячет подписи у кнопок пресета и памяти, оставляя иконки.
 *
 * GUI облачного DSH — это SPA: узлы создаются позже, часть может жить в shadow DOM.
 * Поэтому обходим document, все shadowRoot'ы и iframe, ищем текстовые узлы по
 * подстроке и повторяем это по MutationObserver.
 */
object CompactUi {

    private val LABELS = listOf(
        "standard", "memory on", "memory off", "память вкл", "память выкл",
        "рассуждения", "reasoning", "standard mode",
    )

    fun script(enabled: Boolean): String {
        val labels = LABELS.joinToString(",") { "'" + it.replace("'", "\\'") + "'" }
        return """
            (function(){
              var LABELS = [$labels];
              var ENABLED = $enabled;
              if (!ENABLED) {
                (window.__dshCompactNodes || []).forEach(function(node){
                  if (node.__dshPrev !== undefined && node.parentNode) {
                    node.nodeValue = node.__dshPrev;
                    delete node.__dshPrev;
                  }
                });
                window.__dshCompactNodes = [];
                window.__dshCompactWatching = false;
                return 'off';
              }

              var saved = window.__dshCompactNodes || (window.__dshCompactNodes = []);
              var seen = {};

              function matchText(raw){
                var t = (raw || '').replace(/\u00a0/g, ' ').trim().toLowerCase();
                if (!t || t.length > 32) return false;
                for (var i = 0; i < LABELS.length; i++) {
                  if (t === LABELS[i] || t.indexOf(LABELS[i]) !== -1) return true;
                }
                return false;
              }

              function walk(root, depth){
                if (!root || depth > 24) return;
                var walker;
                try { walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null); } catch (e) { return; }
                var node;
                while ((node = walker.nextNode())) {
                  if (matchText(node.nodeValue) && node.__dshPrev === undefined) {
                    node.__dshPrev = node.nodeValue;
                    node.nodeValue = '';
                    saved.push(node);
                  }
                }
                var all = [];
                try { all = root.querySelectorAll ? root.querySelectorAll('*') : []; } catch (e) { all = []; }
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  if (el.shadowRoot) walk(el.shadowRoot, depth + 1);
                  if (el.tagName === 'IFRAME') {
                    try { if (el.contentDocument) walk(el.contentDocument, depth + 1); } catch (e2) {}
                  }
                }
              }

              function apply(){ walk(document, 0); }

              apply();
              if (window.__dshCompactWatching) return 'applied:' + saved.length;
              window.__dshCompactWatching = true;

              function scan(){
                var texts = [];
                try {
                  var buttons = document.querySelectorAll('button, [role="button"], [contenteditable] ~ *, [class*="composer"] *');
                  for (var i = 0; i < buttons.length && texts.length < 25; i++) {
                    var t = (buttons[i].innerText || '').replace(/\s+/g, ' ').trim();
                    if (t && t.length <= 28 && texts.indexOf(t) === -1) texts.push(t);
                  }
                } catch (e) {}
                return texts.join(' | ');
              }
              window.__dshCompactScan = scan;
              var timer = null;
              new MutationObserver(function(){
                clearTimeout(timer);
                timer = setTimeout(apply, 500);
              }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
              setInterval(apply, 3000);
              return 'watching:' + saved.length;
            })();
        """.trimIndent()
    }

    fun scanScript(): String = "String(window.__dshCompactScan ? window.__dshCompactScan() : 'no scanner')"
}

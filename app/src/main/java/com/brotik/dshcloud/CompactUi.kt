package com.brotik.dshcloud

/**
 * Компактный composer: прячет подписи у кнопок пресета и памяти в поле ввода,
 * оставляя иконки. DOM облачного GUI собирается динамически, поэтому ищем по
 * тексту узлов, а не по классам, и повторяем по MutationObserver.
 */
object CompactUi {

    private val LABELS = listOf(
        "standard", "memory on", "memory off", "память вкл", "память выкл",
        "рассуждения", "reasoning",
    )

    fun script(enabled: Boolean): String {
        val action = if (enabled) "apply" else "undo"
        val labels = LABELS.joinToString(",") { "'" + it.replace("'", "\\'") + "'" }
        return """
            (function(){
              window.__dshCompactLabels = [$labels];
              if (!$enabled && !window.__dshCompactNodes) return 'noop';
              var LABELS = window.__dshCompactLabels;

              function walk(root, fn){
                var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
                var n;
                while ((n = walker.nextNode())) fn(n);
              }

              function mark(el){
                if (!el) return;
                if (el.dataset && el.dataset.dshCompactSaved === undefined) {
                  el.dataset.dshCompactSaved = el.textContent;
                }
                el.style.display = 'none';
              }

              function apply(){
                var saved = window.__dshCompactNodes || (window.__dshCompactNodes = []);
                walk(document.body, function(node){
                  var text = (node.nodeValue || '').trim().toLowerCase();
                  if (!text || LABELS.indexOf(text) === -1) return;
                  if (node.__dshHidden) return;
                  node.__dshHidden = true;
                  node.__dshPrev = node.nodeValue;
                  node.nodeValue = '';
                  saved.push(node);
                });
              }

              function undo(){
                (window.__dshCompactNodes || []).forEach(function(node){
                  if (node.__dshPrev !== undefined) {
                    node.nodeValue = node.__dshPrev;
                    delete node.__dshPrev;
                    delete node.__dshHidden;
                  }
                });
                window.__dshCompactNodes = [];
              }

              if ('$action' === 'undo') { undo(); return 'undone'; }
              if (window.__dshCompactWatching) { apply(); return 'updated'; }
              window.__dshCompactWatching = true;
              apply();
              var timer = null;
              new MutationObserver(function(){
                clearTimeout(timer);
                timer = setTimeout(apply, 400);
              }).observe(document.body, { childList: true, subtree: true, characterData: true });
              setInterval(apply, 3000);
              return 'watching';
            })();
        """.trimIndent()
    }
}

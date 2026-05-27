(function () {
  function normalizeValue(value, type) {
    if (value === null || value === undefined || value === "") {
      return { empty: true, value: null };
    }
    if (type === "number" || type === "yearMonth") {
      var num = Number(value);
      return { empty: Number.isNaN(num), value: Number.isNaN(num) ? null : num };
    }
    return { empty: false, value: String(value).toLowerCase() };
  }

  function compareValues(left, right, type) {
    var a = normalizeValue(left, type);
    var b = normalizeValue(right, type);
    if (a.empty && b.empty) return 0;
    if (a.empty) return 1;
    if (b.empty) return -1;
    if (a.value < b.value) return -1;
    if (a.value > b.value) return 1;
    return 0;
  }

  function cloneSortChain(sortChain) {
    return (sortChain || []).map(function (item) {
      return { key: item.key, direction: item.direction };
    });
  }

  function toggleSort(sortChain, key) {
    var next = cloneSortChain(sortChain);
    var idx = next.findIndex(function (item) { return item.key === key; });
    if (idx === -1) {
      next.push({ key: key, direction: "asc" });
      return next;
    }
    if (next[idx].direction === "asc") {
      next[idx].direction = "desc";
      return next;
    }
    next.splice(idx, 1);
    return next;
  }

  function sortRows(rows, sortChain, columnDefs) {
    var defs = columnDefs || {};
    var chain = sortChain || [];
    if (!chain.length) return rows.slice();
    return rows.slice().sort(function (left, right) {
      for (var i = 0; i < chain.length; i += 1) {
        var item = chain[i];
        var def = defs[item.key] || {};
        var getter = def.getter || function (row) { return row[item.key]; };
        var type = def.type || "text";
        var compared = compareValues(getter(left), getter(right), type);
        if (compared !== 0) {
          return item.direction === "desc" ? compared * -1 : compared;
        }
      }
      return 0;
    });
  }

  function ensureHint(th) {
    var hint = th.querySelector(".sort-hint");
    if (!hint) {
      hint = document.createElement("span");
      hint.className = "sort-hint";
      hint.style.marginLeft = "6px";
      hint.style.fontFamily = "var(--font-data)";
      hint.style.fontSize = "10px";
      hint.style.fontWeight = "700";
      hint.style.color = "var(--color-primary)";
      th.appendChild(hint);
    }
    return hint;
  }

  function renderHeaderState(table, sortChain) {
    if (!table) return;
    var headers = table.querySelectorAll("th[data-sort-key]");
    headers.forEach(function (th) {
      var key = th.getAttribute("data-sort-key");
      var idx = (sortChain || []).findIndex(function (item) { return item.key === key; });
      th.style.cursor = "pointer";
      th.style.userSelect = "none";
      var hint = ensureHint(th);
      if (idx === -1) {
        hint.textContent = "";
        th.style.color = "var(--color-neutral-800)";
      } else {
        var item = sortChain[idx];
        hint.textContent = (idx + 1) + (item.direction === "asc" ? "↑" : "↓");
        th.style.color = "var(--color-primary)";
      }
    });
  }

  function bindTable(table, state, onChange) {
    if (!table || !state) return;
    var headers = table.querySelectorAll("th[data-sort-key]");
    headers.forEach(function (th) {
      if (th.dataset.sortBound === "true") return;
      th.dataset.sortBound = "true";
      th.addEventListener("click", function () {
        var key = th.getAttribute("data-sort-key");
        state.sortChain = toggleSort(state.sortChain, key);
        renderHeaderState(table, state.sortChain);
        if (typeof onChange === "function") onChange(state.sortChain);
      });
    });
    renderHeaderState(table, state.sortChain);
  }

  window.ApsTableMultiSort = {
    bindTable: bindTable,
    renderHeaderState: renderHeaderState,
    sortRows: sortRows,
    toggleSort: toggleSort
  };
}());

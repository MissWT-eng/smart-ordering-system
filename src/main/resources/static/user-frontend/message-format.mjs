// Only render a small Markdown subset. Model text never becomes raw HTML or executable URLs.
export const escapeHtml = (text) => String(text ?? "").replace(/[&<>"']/g,
  (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);

function inline(text) {
  return escapeHtml(text).replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
}

export function formatMessage(text) {
  const lines = String(text ?? "").replace(/\r\n?/g, "\n").split("\n");
  const html = [];
  let list = null, code = null;
  const closeList = () => { if (list) html.push(`</${list}>`); list = null; };
  for (let index = 0; index < lines.length; index++) {
    const value = lines[index];
    if (value.startsWith("```")) {
      closeList();
      if (code) { html.push(`<pre><code>${escapeHtml(code.join("\n"))}</code></pre>`); code = null; }
      else code = [];
      continue;
    }
    if (code) { code.push(value); continue; }
    if (!value.trim()) { closeList(); continue; }
    if (value.includes("|") && /^\s*\|?\s*:?-{3,}:?\s*\|/.test(lines[index + 1] || "")) {
      closeList();
      const cells = (row) => row.trim().replace(/^\||\|$/g, "").split("|").map((cell) => inline(cell.trim()));
      const head = cells(value);
      index += 2;
      const rows = [];
      while (index < lines.length && lines[index].includes("|") && lines[index].trim()) {
        rows.push(`<tr>${cells(lines[index]).map((cell) => `<td>${cell}</td>`).join("")}</tr>`);
        index++;
      }
      index--;
      html.push(`<div class="table-scroll"><table><thead><tr>${head.map((cell) => `<th>${cell}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`);
      continue;
    }
    const bullet = value.match(/^\s*([-*]|\d+[.)、])\s+(.+)$/);
    if (bullet) {
      const tag = /^\d/.test(bullet[1]) ? "ol" : "ul";
      if (list !== tag) { closeList(); html.push(`<${tag}>`); list = tag; }
      html.push(`<li>${inline(bullet[2])}</li>`);
    } else {
      closeList();
      const heading = value.match(/^#{1,6}\s+(.+)$/);
      html.push(heading ? `<h3>${inline(heading[1])}</h3>` : `<p>${inline(value)}</p>`);
    }
  }
  closeList();
  if (code) html.push(`<pre><code>${escapeHtml(code.join("\n"))}</code></pre>`);
  return html.join("");
}

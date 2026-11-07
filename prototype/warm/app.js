const app = document.getElementById('app');
const editor = document.getElementById('editor');
const seed = [
  { id: 1, kind: 'todo', title: '给阳台的绿植浇水', note: '天气转凉，少浇一点', pinned: true },
  { id: 2, kind: 'todo', title: '把周末的照片洗出来', note: '选几张，寄给爸爸妈妈' },
  { id: 3, kind: 'todo', title: '取回干洗的外套', note: '' },
  { id: 4, kind: 'todo', title: '读完床头的那本书', note: '留一点时间给自己' },
  { id: 5, kind: 'todo', title: '整理书桌', note: '', done: true },
  { id: 6, kind: 'shopping', title: '燕麦奶', note: '原味，无糖', quantity: '2', unit: '盒', pinned: true },
  { id: 7, kind: 'shopping', title: '全麦面包', note: '', quantity: '1', unit: '袋' },
  { id: 8, kind: 'shopping', title: '一束鲜花', note: '给餐桌添一点颜色', quantity: '1', unit: '束' },
  { id: 9, kind: 'shopping', title: '鸡蛋', note: '', quantity: '1', unit: '盒' }
];
let items = structuredClone(seed);
let scene = 'todo';
let kind = 'todo';
let completed = false;
let query = '';
let nextId = 10;
let undoTimer;
let lastFocus;
const config = { kind: 'todo', size: 'large', theme: 'system', density: 'regular' };
const icons = {
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/>',
  todo: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  shopping: '<path d="M5 8h14l1 12H4L5 8Z M8 8V6a4 4 0 0 1 8 0v2"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3Z"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${icons[name]}</svg>`;
const escapeText = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));
const listName = value => value === 'todo' ? '随手待办' : '日常采购';
const active = value => items.filter(item => item.kind === value && !item.done).sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned));

function row(item, widget = false) {
  return `<div class="item ${item.done ? 'done' : ''}">
    <button class="check" data-complete="${item.id}" aria-label="${item.done ? '恢复' : '完成'}${escapeText(item.title)}"><span class="circle">${item.done ? '✓' : ''}</span></button>
    <button class="item-title" data-edit="${item.id}"><span>${escapeText(item.title)}</span>${item.pinned && !widget ? '<b class="pin">置顶</b>' : ''}${item.note && !widget ? `<small>${escapeText(item.note)}</small>` : ''}</button>
    ${item.quantity ? `<span class="quantity">${escapeText(item.quantity)} ${escapeText(item.unit)}</span>` : ''}
  </div>`;
}

function widget(value, limit) {
  const rows = active(value);
  return `<article class="widget" aria-label="${listName(value)}小部件">
    <div class="widget-header"><button data-open-list="${value}">${listName(value)}</button><span class="count">${rows.length} ${value === 'todo' ? '件小事' : '件待买'}</span></div>
    ${rows.length ? rows.slice(0, limit).map(item => row(item, true)).join('') : '<p class="empty">清单空空，心里轻轻。</p>'}
    <div class="widget-footer"><button data-add="${value}">＋ 记一条</button><button data-open-list="${value}">查看全部 ›</button></div>
  </article>`;
}

function renderWidgets() {
  document.getElementById('widgets').innerHTML = widget('todo', 4) + widget('shopping', 2);
}

function nav() {
  return `<nav class="app-nav" aria-label="应用导航">${['todo','shopping','settings'].map(value => `<button data-scene="${value}" class="${scene === value ? 'active' : ''}" ${scene === value ? 'aria-current="page"' : ''}>${icon(value)}<span>${{todo:'待办',shopping:'待买',settings:'设置'}[value]}</span></button>`).join('')}</nav>`;
}

function head(title, sub, overline = '忘了没 · 给生活留一点空白') {
  return `<header class="app-head"><div class="overline"><span class="sun">☼</span>${overline}</div><div class="heading-row"><h2>${title}</h2></div><p class="sub">${sub}</p></header>`;
}

function renderRows() {
  const rows = items.filter(item => item.kind === kind && !!item.done === completed && item.title.toLocaleLowerCase().includes(query.toLocaleLowerCase())).sort((a,b) => Number(!!b.pinned) - Number(!!a.pinned));
  document.getElementById('rows').innerHTML = rows.length ? `<div class="paper-list">${rows.map(item => row(item)).join('')}</div>` : `<div class="empty">${query ? '没有找到相关记录，换个词试试。' : completed ? '这里还没有已完成的记录。' : '清单空空，心里轻轻。<br>想到什么，就记一条吧。'}</div>`;
}

function render() {
  document.querySelectorAll('.scenes button').forEach(button => {
    button.classList.toggle('selected', button.dataset.scene === scene);
    button.setAttribute('aria-pressed', String(button.dataset.scene === scene));
  });
  if (scene === 'todo' || scene === 'shopping') {
    kind = scene;
    const count = active(kind).length;
    app.innerHTML = head(kind === 'todo' ? '慢慢来，一件件。' : '把喜欢的，带回家。', `${count} 件${kind === 'todo' ? '小事，按自己的节奏来' : '东西，记得带回家'}`) + `
      <div class="toolbar"><div class="list-controls"><select id="list-select" aria-label="当前清单"><option>${listName(kind)}</option></select><button class="link-button" data-manage>管理清单</button></div>
      <label class="search-box">${icon('search')}<input id="search" aria-label="搜索这份清单" placeholder="搜索这份清单" value="${escapeText(query)}"></label>
      <div class="filterbar"><div class="segments" aria-label="完成状态"><button data-filter="active" class="${!completed ? 'active' : ''}" aria-pressed="${!completed}">未${kind === 'todo' ? '完成' : '购买'}</button><button data-filter="done" class="${completed ? 'active' : ''}" aria-pressed="${completed}">已${kind === 'todo' ? '完成' : '购买'}</button></div><span class="count">${count} 项待${kind === 'todo' ? '办' : '买'}</span></div></div>
      <div class="list-area"><div id="rows"></div><p class="list-footnote">记下来，就不用一直放在心上。</p></div>
      <div class="bottom"><button class="primary full" data-add="${kind}">＋　记一条</button></div>${nav()}`;
    renderRows();
  } else if (scene === 'config') {
    app.innerHTML = head('桌面上的小陪伴', '选一份清单，留在最顺手的地方。') + `<div class="content">
      <div class="config-card" id="config-preview"></div>
      <label class="form-field"><span>绑定清单</span><select id="config-kind"><option value="todo">随手待办</option><option value="shopping">日常采购</option></select></label>
      <label class="form-field"><span>小部件尺寸 · 原型预览</span><select id="config-size"><option value="large">大清单 · 4 × 4</option><option value="small">标准清单 · 4 × 2</option></select></label>
      <label class="form-field"><span>外观</span><select id="config-theme"><option value="system">跟随系统</option><option value="light">浅米色</option><option value="dark">暖深色</option></select></label>
      <label class="form-field"><span>内容密度</span><select id="config-density"><option value="regular">舒适</option><option value="compact">紧凑</option></select></label>
      <button class="primary full" data-config-save>保存配置</button><p class="helper">尺寸由系统桌面决定，这里的尺寸选择用于演示。</p></div>`;
    for (const key of Object.keys(config)) document.getElementById(`config-${key}`).value = config[key];
    renderConfig();
  } else if (scene === 'settings') {
    app.innerHTML = head('用起来，刚刚好。', '按自己的习惯，照顾这份小清单。') + `<div class="content">
      <p class="section-label">外观与桌面</p><div class="setting-group">
      <label class="setting-row"><span>应用主题<small>让眼睛也放松一点</small></span><select id="app-theme"><option value="light">暖米色</option><option value="dark">暖深色</option></select></label>
      <button class="setting-row full" data-scene="config"><span>桌面小部件<small>清单、外观与密度</small></span><span>›</span></button></div>
      <p class="section-label">清单与数据</p><div class="setting-group"><button class="setting-row full" data-manage><span>管理清单</span><span>›</span></button><button class="setting-row full" data-info="备份与恢复沿用现有 SAF 导入导出流程，详见开发说明。"><span>备份与恢复<small>把重要的小事好好收着</small></span><span>›</span></button></div>
      <p class="section-label">关于忘了没</p><div class="setting-group"><button class="setting-row full" data-info="检查更新沿用现有下载、校验及安装流程，原型展示视觉入口。"><span>检查更新</span><span>›</span></button></div><p class="helper">本页为视觉与交互提案，数据操作使用演示入口。</p></div>${nav()}`;
    document.getElementById('app-theme').value = document.body.classList.contains('dark') ? 'dark' : 'light';
  }
}

function renderConfig() {
  const preview = document.getElementById('config-preview');
  preview.classList.toggle('dark', config.theme === 'dark');
  preview.style.cssText = config.theme === 'dark' ? '--glass:rgba(49,41,32,.9);--glass-text:#f4eadc;--glass-muted:#d3c2ac;--glass-primary:#e5b68e;--glass-edge:#ffedd630' : config.theme === 'light' ? '--glass:rgba(255,248,234,.8);--glass-text:#383129;--glass-muted:#6f6050;--glass-primary:#8a5335;--glass-edge:#ffffffb8' : '';
  preview.innerHTML = widget(config.kind, config.size === 'large' ? 3 : 1);
  preview.querySelectorAll('.item').forEach(item => { item.style.minHeight = config.density === 'compact' ? '48px' : '52px'; });
}

function toast(message, undo) {
  clearTimeout(undoTimer);
  const host = document.getElementById('toast');
  host.innerHTML = `<div class="toast"><span>${escapeText(message)}</span>${undo ? '<button id="undo">撤销</button>' : ''}</div>`;
  if (undo) document.getElementById('undo').onclick = () => { undo(); host.innerHTML = ''; clearTimeout(undoTimer); };
  undoTimer = setTimeout(() => { host.innerHTML = ''; }, 5000);
}

function refresh() { render(); renderWidgets(); }

function openEditor(value, itemId) {
  const item = items.find(entry => entry.id === itemId);
  lastFocus = document.activeElement;
  editor.innerHTML = `<div class="handle"></div><div class="sheet-title"><h2 id="editor-heading">${item ? '编辑这一条' : '想到，就记下来。'}</h2><button data-close>取消</button></div>
    <form id="entry-form"><label class="form-field"><span>要记的内容</span><textarea name="title" class="title-input" maxlength="200" placeholder="有什么想记下来的？" required>${escapeText(item?.title)}</textarea></label>
    <label class="form-field"><span>放进哪份清单</span><select name="kind"><option value="todo">待办 · 随手待办</option><option value="shopping">待买 · 日常采购</option></select></label>
    <div class="inline" id="quantity-fields"><label class="form-field"><span>数量</span><input name="quantity" type="number" min="0.01" max="9999.99" step="0.01" value="${escapeText(item?.quantity)}" placeholder="可选"></label><label class="form-field"><span>单位</span><input name="unit" maxlength="12" value="${escapeText(item?.unit)}" placeholder="盒、袋、束"></label></div>
    <label class="form-field"><span>备注 · 可选</span><textarea name="note" maxlength="2000" rows="2" placeholder="补充一点小细节">${escapeText(item?.note)}</textarea></label>
    <label class="toggle-label"><input type="checkbox" name="pinned" ${item?.pinned ? 'checked' : ''}>置顶，让它先被看见</label>
    <p class="error" id="entry-error" role="alert"></p><div class="inline"><button class="secondary" type="button" data-close>取消</button><button class="primary" type="submit">${item ? '保存修改' : '记好了'}</button></div><p class="helper">当前演示单条记录。批量输入与长期草稿见开发说明。</p></form>`;
  const form = document.getElementById('entry-form');
  form.elements.kind.value = item?.kind || value;
  const quantityVisibility = () => { document.getElementById('quantity-fields').hidden = form.elements.kind.value !== 'shopping'; };
  quantityVisibility();
  form.elements.kind.onchange = quantityVisibility;
  form.onsubmit = event => {
    event.preventDefault();
    const title = form.elements.title.value.trim();
    if (!title) { document.getElementById('entry-error').textContent = '先写一点想记下来的事吧。'; return; }
    const targetKind = form.elements.kind.value;
    if (targetKind === 'shopping' && form.elements.unit.value.trim() && !form.elements.quantity.value) {
      document.getElementById('entry-error').textContent = '填写单位时，请同时填写数量。';
      return;
    }
    if (item?.kind === 'shopping' && targetKind === 'todo' && !window.confirm('移到待办清单会清除数量和单位，是否继续？')) return;
    const entry = { id:item?.id || nextId++, kind:targetKind, title, note:form.elements.note.value.trim(), pinned:form.elements.pinned.checked, done:item?.done || false, quantity:targetKind === 'shopping' ? form.elements.quantity.value : '', unit:targetKind === 'shopping' ? form.elements.unit.value.trim() : '' };
    if (item) Object.assign(item, entry); else items.push(entry);
    editor.close();
    if (scene === 'todo' || scene === 'shopping') { scene = targetKind; completed = false; query = ''; }
    refresh(); toast('记好了，放下心来。');
  };
  const phoneBounds = document.querySelector('.phone').getBoundingClientRect();
  const sheetTop = Math.max(12, phoneBounds.top + 66);
  Object.assign(editor.style, {
    position: 'fixed', left: `${phoneBounds.left + 5}px`, top: `${sheetTop}px`,
    bottom: 'auto', right: 'auto', width: `${phoneBounds.width - 10}px`,
    maxHeight: `${Math.max(240, Math.min(phoneBounds.height - 72, window.innerHeight - sheetTop - 12))}px`
  });
  editor.showModal();
  form.elements.title.focus();
}

document.addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  const data = button.dataset;
  if (data.scene) {
    if (data.scene === 'editor') { openEditor(kind); return; }
    scene = data.scene; query = ''; completed = false; render();
  } else if (data.complete) {
    const item = items.find(entry => entry.id === Number(data.complete));
    item.done = !item.done;
    refresh();
    toast(item.done ? '又完成了一件小事。' : '已放回清单。', () => { item.done = !item.done; refresh(); });
  } else if (data.edit) openEditor(kind, Number(data.edit));
  else if (data.add) openEditor(data.add);
  else if (data.openList) { scene = data.openList; completed = false; query = ''; render(); }
  else if (data.filter) { completed = data.filter === 'done'; render(); }
  else if ('close' in data) editor.close();
  else if ('configSave' in data) toast('配置已应用到此预览。');
  else if ('manage' in data) toast('当前演示两份默认清单。自定义清单沿用现有管理流程。');
  else if (data.info) toast(data.info);
});
document.addEventListener('input', event => {
  if (event.target.id === 'search') { query = event.target.value; renderRows(); }
});
document.addEventListener('change', event => {
  if (event.target.id.startsWith('config-')) { config[event.target.id.slice(7)] = event.target.value; renderConfig(); }
  if (event.target.id === 'app-theme') setTheme(event.target.value === 'dark');
});
editor.addEventListener('close', () => { if (lastFocus?.isConnected) lastFocus.focus(); });
function setTheme(dark) {
  document.body.classList.toggle('dark', dark);
  document.getElementById('theme').textContent = dark ? '日间预览' : '夜间预览';
}
document.getElementById('theme').onclick = () => { setTheme(!document.body.classList.contains('dark')); render(); };
document.getElementById('wall').onclick = event => {
  const dark = document.getElementById('wallpaper').classList.toggle('dark-wall');
  event.currentTarget.textContent = dark ? '切换浅色壁纸' : '切换深色壁纸';
};
document.getElementById('material').onclick = event => {
  const native = document.getElementById('wallpaper').classList.toggle('native');
  event.currentTarget.setAttribute('aria-pressed', String(native));
  event.currentTarget.textContent = native ? '查看设计目标' : '查看原生近似';
  document.getElementById('material-note').textContent = native ? '原生近似 · 浅色 92% / 深色 94% 透光层 + 高光描边' : '设计目标 · 背景模糊 24px + 浅米色透光层';
};
document.getElementById('reset').onclick = () => {
  items = structuredClone(seed); nextId = 10; scene = 'todo'; query = ''; completed = false;
  refresh(); toast('已恢复演示数据。');
};
refresh();

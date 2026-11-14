const app = document.getElementById('app');
const editor = document.getElementById('editor');
const phone = document.getElementById('phone');

const seedLists = [
  { id: 'todo', name: '随手待办', isDefault: true },
  { id: 'weekend', name: '周末出行', isDefault: false },
  { id: 'reading', name: '生活灵感', isDefault: false }
];

const seedItems = [
  { id: 1, listId: 'todo', title: '给阳台的绿植浇水', note: '天气转凉，少浇一点', pinned: true, done: false },
  { id: 2, listId: 'todo', title: '把周末的照片洗出来', note: '选几张，寄给爸爸妈妈', pinned: false, done: false },
  { id: 3, listId: 'todo', title: '取回干洗的外套', note: '周六下午顺路去拿', pinned: false, done: false },
  { id: 4, listId: 'todo', title: '补充家里常备的燕麦奶与咖啡豆', note: '原味无糖 2 盒 · 浅烘手冲豆', pinned: false, done: false },
  { id: 5, listId: 'todo', title: '整理书桌抽屉里的旧票据', note: '', pinned: false, done: true },
  { id: 6, listId: 'weekend', title: '预订周六去植物园的门票', note: '带上胶片相机和野餐垫', pinned: true, done: false },
  { id: 7, listId: 'weekend', title: '给移动电源和耳机充满电', note: '', pinned: false, done: false },
  { id: 8, listId: 'reading', title: '读完床头那本《日日是好日》', note: '留半小时安静时间给自己', pinned: true, done: false },
  { id: 9, listId: 'reading', title: '记录秋日散步拍到的银杏配色', note: '', pinned: false, done: false }
];

let lists = structuredClone(seedLists);
let items = structuredClone(seedItems);
let scene = 'todo';
let currentListId = 'todo';
let completed = false;
let query = '';
let nextId = 10;
let undoTimer;
let widgetUndo = null;
let lastFocus;
let updateStatus = 'idle';
let renamingListId = null;
let draft = { title: '', note: '', listId: 'todo', pinned: false, batch: false };

const config = {
  listId: 'todo',
  size: 'large',
  theme: 'system',
  density: 'regular'
};

const icons = {
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 4.5 4.5"/>',
  lists: '<path d="M4 6.5h16M4 12h16M4 17.5h10"/><circle cx="18" cy="17.5" r="2.2"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.6.77 1.02 1.41 1.09H21a2 2 0 1 1 0 4h-.09c-.64.07-1.15.49-1.51 1.01Z"/>',
  pin: '<path d="m15 4.5-4 4L7 10l-1.5 1.5 7 7L14 17l1.5-4 4-4-4.5-4.5Z"/><path d="m9 15-4.5 4.5"/>',
  pen: '<path d="M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/>',
  export: '<path d="M12 3v12"/><path d="m8 11 4 4 4-4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
  import: '<path d="M12 15V3"/><path d="m8 7 4-4 4 4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
  spark: '<path d="M12 3v3m0 12v3M3 12h3m12 0h3m-2.6-6.4-2.1 2.1M8.7 15.3l-2.1 2.1m0-11.8 2.1 2.1m6.6 6.6 2.1 2.1"/>'
};

const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${icons[name]}</svg>`;

const escapeText = value =>
  String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]));

const pad2 = n => String(n).padStart(2, '0');
const getList = listId => lists.find(l => l.id === listId) || lists[0];
const activeItems = listId =>
  items
    .filter(item => item.listId === listId && !item.done)
    .sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned) || a.id - b.id);

const completedItems = listId =>
  items
    .filter(item => item.listId === listId && !!item.done)
    .sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned) || b.id - a.id);

function row(item, index = 0, isWidget = false) {
  const pinnedClass = item.pinned && !item.done && !isWidget ? 'pinned-row' : '';
  return `<div class="item ${item.done ? 'done' : ''} ${pinnedClass}">
    <button class="check" data-complete="${item.id}" aria-label="${item.done ? '恢复' : '完成'}${escapeText(item.title)}">
      <span class="circle"><span>${item.done ? '✓' : ''}</span></span>
    </button>
    <button class="item-title" data-edit="${item.id}">
      <span class="item-title-line">
        ${!isWidget ? `<span class="row-idx">${pad2(index + 1)}</span>` : ''}
        <span class="item-title-text">${escapeText(item.title)}</span>
        ${item.pinned ? `<span class="stamp-badge">置顶</span>` : ''}
        ${item.done && !isWidget ? `<span class="done-badge">已归档</span>` : ''}
      </span>
      ${item.note && !isWidget ? `<small>${escapeText(item.note)}</small>` : ''}
    </button>
    ${!isWidget ? `<button class="pin-toggle ${item.pinned ? 'active' : ''}" data-pin="${item.id}" title="${item.pinned ? '取消置顶' : '置顶这条记录'}" aria-label="${item.pinned ? '取消置顶' : '置顶'}">${icon('pin')}</button>` : ''}
  </div>`;
}

function renderWidgetCard({ listId, size = 'large', density = 'regular', isPreview = false }) {
  const list = getList(listId);
  const rows = activeItems(list.id);
  const doneCount = completedItems(list.id).length;
  const isCompact = density === 'compact' || density === 'ultra';
  const limit = size === 'large'
    ? (density === 'ultra' ? 6 : density === 'compact' ? 5 : 4)
    : (density === 'ultra' ? 3 : 2);
  const modeClass = density === 'ultra' ? 'ultra-mode' : density === 'compact' ? 'compact-mode' : '';
  const hasUndoHere = widgetUndo && widgetUndo.listId === list.id && !isPreview;

  const headerTitle = isCompact && hasUndoHere
    ? `<button class="widget-title-btn" data-widget-undo>已完成 · 点此撤销</button>`
    : `<button class="widget-title-btn" data-open-list="${list.id}">
        <span class="widget-dot" aria-hidden="true"></span>
        <span>${escapeText(list.name)}</span>
        ${isCompact ? `<span class="count-inline">· ${pad2(rows.length)} ›</span>` : ''}
      </button>`;

  const headerRight = isCompact
    ? `<button class="widget-header-add" data-add="${list.id}" aria-label="在${escapeText(list.name)}记一条">＋</button>`
    : `<span class="count">${pad2(rows.length)} 项</span>`;

  const emptyCopy = doneCount > 0 ? '都完成了，心里轻轻。' : '清单空空，心里轻轻。';

  const footer = !isCompact
    ? `<div class="widget-footer">
        <button data-add="${list.id}">＋ 记一笔</button>
        ${hasUndoHere
          ? `<button data-widget-undo>已完成 · 撤销</button>`
          : `<button data-open-list="${list.id}">查看全部 ›</button>`}
      </div>`
    : '';

  return `<article class="widget ${modeClass}" aria-label="${escapeText(list.name)}小部件">
    <div class="widget-header">${headerTitle}${headerRight}</div>
    ${rows.length ? rows.slice(0, limit).map((item, idx) => row(item, idx, true)).join('') : `<p class="empty">${emptyCopy}</p>`}
    ${footer}
  </article>`;
}

function renderWidgets() {
  const secondaryList = lists.find(l => l.id !== config.listId) || lists[0];
  document.getElementById('widgets').innerHTML =
    renderWidgetCard({
      listId: config.listId,
      size: config.size,
      density: config.density
    }) +
    renderWidgetCard({
      listId: secondaryList.id,
      size: 'small',
      density: 'compact'
    });
}

function renderRows() {
  const container = document.getElementById('rows');
  if (!container) return;
  const rows = items
    .filter(item =>
      item.listId === currentListId &&
      !!item.done === completed &&
      (item.title.toLocaleLowerCase().includes(query.toLocaleLowerCase()) ||
        (item.note || '').toLocaleLowerCase().includes(query.toLocaleLowerCase()))
    )
    .sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned) || a.id - b.id);

  if (rows.length) {
    const ghostLinesHtml = !query && !completed && rows.length < 6
      ? `<div class="ghost-lines" aria-hidden="true">
          <button type="button" class="ghost-row" data-add="${currentListId}" tabindex="-1">
            <span class="ghost-gutter">·</span>
            <span class="ghost-content">
              <span class="row-idx">${pad2(rows.length + 1)}</span>
              <span class="ghost-hint">在空白格落笔，再记一件小事…</span>
            </span>
          </button>
          <div class="ghost-row static">
            <span class="ghost-gutter">·</span>
            <span class="ghost-content">
              <span class="row-idx">${pad2(rows.length + 2)}</span>
              <span class="ghost-motto">日日是好日 · 宜轻装慢行</span>
            </span>
          </div>
        </div>`
      : '';
    container.innerHTML = `<div class="paper-rows">${rows.map((item, idx) => row(item, idx)).join('')}${ghostLinesHtml}</div>`;
  } else {
    const activeCount = activeItems(currentListId).length;
    const doneCount = completedItems(currentListId).length;
    const message = query
      ? '没有找到相关记录，换个词试试。'
      : completed
        ? '这里还没有已归档的记录。'
        : activeCount === 0 && doneCount > 0
          ? '这一页都完成了，心里轻轻。<br>点下方钢笔铭牌再记一笔吧。'
          : '纸页空空，心里轻轻。<br>想到什么，就随手落笔。';
    container.innerHTML = `<div class="empty-state"><span class="empty-seal">空</span><p>${message}</p></div>`;
  }
}

function render() {
  document.querySelectorAll('.scenes button').forEach(button => {
    const isSelected = button.dataset.scene === scene;
    button.classList.toggle('selected', isSelected);
    button.setAttribute('aria-pressed', String(isSelected));
  });

  if (!lists.some(l => l.id === currentListId)) {
    currentListId = lists[0].id;
  }

  if (scene === 'todo') {
    const activeCount = activeItems(currentListId).length;
    const doneCount = completedItems(currentListId).length;
    const totalActiveAll = items.filter(i => !i.done).length;
    const isFirstTab = lists[0]?.id === currentListId;
    const subCopy = activeCount > 0
      ? `${pad2(activeCount)} 件小事待办 · 周日宜轻装`
      : doneCount > 0
        ? '今日事毕 · 心里轻轻'
        : '纸页空空 · 随时落笔';

    app.innerHTML = `
      <header class="app-head">
        <div class="editorial-header">
          <div class="almanac-left">
            <div class="date-stamp" aria-hidden="true">
              <span class="m">NOV</span>
              <span class="d">15</span>
            </div>
            <div class="title-block">
              <h2>慢慢来，一件件。</h2>
              <p class="sub">${subCopy}</p>
            </div>
          </div>
          <div class="top-actions">
            <button class="tactile-btn" data-scene="manage" aria-label="管理清单" title="管理清单">
              ${icon('lists')}<span>目录</span>
            </button>
            <button class="tactile-icon-btn" data-scene="settings" aria-label="设置" title="设置">
              ${icon('settings')}
            </button>
          </div>
        </div>
      </header>

      <nav class="index-tabs-bar" aria-label="手账索引耳切换">
        ${lists.map(list => {
          const cnt = activeItems(list.id).length;
          const isCurrent = list.id === currentListId;
          return `<button class="index-tab bookmark-chip ${isCurrent ? 'active' : ''}" data-select-list="${list.id}" aria-pressed="${isCurrent}">
            <span class="tab-label">${escapeText(list.name)}</span>
            <span class="tab-num">${pad2(cnt)}</span>
          </button>`;
        }).join('')}
        <button class="index-tab-add" data-scene="manage" aria-label="新建或管理清单">＋ 新册</button>
      </nav>

      <div class="list-area">
        <div class="notebook-sheet ${isFirstTab ? 'first-tab-active' : ''}">
          <div class="sheet-running-header">
            <div class="ledger-tabs" role="group" aria-label="完成状态">
              <button data-filter="active" class="ledger-btn ${!completed ? 'active' : ''}" aria-pressed="${!completed}">
                <span>待办</span><small>${pad2(activeCount)}</small>
              </button>
              <button data-filter="done" class="ledger-btn ${completed ? 'active' : ''}" aria-pressed="${completed}">
                <span>已归档</span><small>${pad2(doneCount)}</small>
              </button>
            </div>
            <label class="search-inline">
              ${icon('search')}
              <input id="search" aria-label="搜索这份清单" placeholder="检索本页…" value="${escapeText(query)}">
              <button type="button" id="clear-search" class="clear-search" data-clear-search aria-label="清除搜索" ${query ? '' : 'hidden'}>×</button>
            </label>
          </div>

          <div id="rows"></div>

          <div class="sheet-footer">
            <span>FOLIO · ${escapeText(getList(currentListId).name)}</span>
            <span class="footer-ornament">✦</span>
            <span>ALL PENDING · ${pad2(totalActiveAll)}</span>
          </div>
        </div>
      </div>

      <div class="floating-dock">
        <button class="ink-island fab-pill" data-add="${currentListId}">
          <span class="island-left">
            <span class="island-seal">＋</span>
            <span class="island-label">提笔记一笔</span>
          </span>
          <span class="island-tag">入册「${escapeText(getList(currentListId).name)}」 ↗</span>
        </button>
      </div>
    `;
    renderRows();
  } else if (scene === 'manage') {
    const totalPending = items.filter(i => !i.done).length;
    const totalDone = items.filter(i => i.done).length;
    app.innerHTML = `
      <header class="app-head">
        <button class="back-btn" data-scene="todo">← 返回手账</button>
        <div class="title-block">
          <h2>书签与分册目录</h2>
          <p class="sub">默认分册可改名不可删；删册时其下记录自动迁回默认册。</p>
        </div>
      </header>
      <div class="content">
        <div class="setting-group">
          ${lists.map((list, idx) => {
            const total = items.filter(i => i.listId === list.id).length;
            const active = activeItems(list.id).length;
            const isRenaming = renamingListId === list.id;
            return `<div class="manage-row">
              <div class="manage-info">
                <div class="manage-title-line">
                  <span class="manage-idx">${pad2(idx + 1)}</span>
                  ${isRenaming
                    ? `<input id="rename-input-${list.id}" class="inline-rename-input" value="${escapeText(list.name)}" maxlength="24" aria-label="重命名分册">`
                    : `<strong>${escapeText(list.name)}</strong>`}
                  ${list.isDefault ? '<span class="default-badge">默认常驻</span>' : ''}
                </div>
                <small class="manage-meta">
                  ${pad2(active)} 项待办 · 共 ${pad2(total)} 条记录${list.isDefault ? ' · 不可删除' : ''}
                </small>
              </div>
              <div class="manage-actions">
                ${isRenaming
                  ? `<button class="mini-btn primary-mini" data-save-rename="${list.id}">保存</button>
                     <button class="mini-btn" data-cancel-rename>取消</button>`
                  : `<button class="mini-btn" data-rename-list="${list.id}">改名</button>
                     <button class="mini-btn danger" data-delete-list="${list.id}" ${list.isDefault ? 'disabled' : ''}>删除</button>`}
              </div>
            </div>`;
          }).join('')}
        </div>

        <form id="create-list-form" class="inline-create">
          <input name="listName" maxlength="24" placeholder="新分册名称（如：秋日旅行打包）" aria-label="新清单名称" required>
          <button type="submit" class="primary">＋ 添一册</button>
        </form>
        <p class="helper">分册名称全局唯一。添册后将作为顶部活页索引耳展示。</p>

        <div class="ex-libris-card" aria-label="手账藏书票统计">
          <div class="ex-libris-head">
            <span>EX LIBRIS · 藏书印记</span>
            <span>V0.4.0 ARCHIVE</span>
          </div>
          <div class="ex-libris-stats">
            <div><b>${pad2(lists.length)}</b><small>立册总数</small></div>
            <div><b>${pad2(totalPending)}</b><small>待办小事</small></div>
            <div><b>${pad2(totalDone)}</b><small>已归档页</small></div>
          </div>
        </div>
      </div>
    `;
    if (renamingListId) {
      const inp = document.getElementById(`rename-input-${renamingListId}`);
      if (inp) {
        inp.focus();
        inp.select();
        inp.onkeydown = e => {
          if (e.key === 'Enter') {
            e.preventDefault();
            commitRename(renamingListId, inp.value);
          } else if (e.key === 'Escape') {
            renamingListId = null;
            render();
          }
        };
      }
    }
    const form = document.getElementById('create-list-form');
    form.onsubmit = event => {
      event.preventDefault();
      const name = form.elements.listName.value.trim();
      if (!name) return;
      if (lists.some(l => l.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
        toast('已存在同名分册，请换个名字。');
        return;
      }
      const id = 'list-' + Date.now();
      lists.push({ id, name, isDefault: false });
      currentListId = id;
      refresh();
      toast(`已添新册「${name}」`);
    };
  } else if (scene === 'config') {
    app.innerHTML = `
      <header class="app-head config-head">
        <button class="back-btn" data-scene="todo">← 返回手账</button>
        <div class="title-block">
          <h2>这张卡片，记什么？</h2>
          <p class="sub">每个桌面小部件可独立绑定分册、外观与三档密度。</p>
        </div>
      </header>
      <div class="content config-content">
        <div class="config-card" id="config-preview"></div>
        <p class="config-meta" id="config-meta"></p>

        <div class="setting-group compact-settings">
          <div class="setting-row" style="display:block">
            <span>绑定分册</span>
            <div class="chip-group">
              ${lists.map(l => `<button type="button" class="choice-chip ${config.listId === l.id ? 'active' : ''}" data-cfg-key="listId" data-cfg-val="${l.id}">${escapeText(l.name)}</button>`).join('')}
            </div>
          </div>
          <div class="setting-row" style="display:block">
            <span>尺寸规格（桌面预览）</span>
            <div class="chip-group">
              <button type="button" class="choice-chip ${config.size === 'large' ? 'active' : ''}" data-cfg-key="size" data-cfg-val="large">大清单 · 4 × 4</button>
              <button type="button" class="choice-chip ${config.size === 'small' ? 'active' : ''}" data-cfg-key="size" data-cfg-val="small">标准清单 · 4 × 2</button>
            </div>
          </div>
          <div class="setting-row" style="display:block">
            <span>卡片材质外观</span>
            <div class="chip-group">
              <button type="button" class="choice-chip ${config.theme === 'system' ? 'active' : ''}" data-cfg-key="theme" data-cfg-val="system">跟随系统</button>
              <button type="button" class="choice-chip ${config.theme === 'light' ? 'active' : ''}" data-cfg-key="theme" data-cfg-val="light">浅米透光</button>
              <button type="button" class="choice-chip ${config.theme === 'dark' ? 'active' : ''}" data-cfg-key="theme" data-cfg-val="dark">暖深茶色</button>
            </div>
          </div>
          <div class="setting-row" style="display:block">
            <span>排版密度<small>紧凑与超紧凑将「＋」收进标题栏，释放底栏空间</small></span>
            <div class="chip-group">
              <button type="button" class="choice-chip ${config.density === 'regular' ? 'active' : ''}" data-cfg-key="density" data-cfg-val="regular">舒适 (保留底栏)</button>
              <button type="button" class="choice-chip ${config.density === 'compact' ? 'active' : ''}" data-cfg-key="density" data-cfg-val="compact">紧凑 (48dp)</button>
              <button type="button" class="choice-chip ${config.density === 'ultra' ? 'active' : ''}" data-cfg-key="density" data-cfg-val="ultra">超紧凑 (36dp)</button>
            </div>
          </div>
        </div>
        <div class="config-save-bar">
          <button class="primary full" data-config-save>保存配置并更新桌面</button>
        </div>
      </div>
    `;
    renderConfig();
  } else if (scene === 'settings') {
    const isDark = document.body.classList.contains('dark');
    app.innerHTML = `
      <header class="app-head">
        <button class="back-btn" data-scene="todo">← 返回手账</button>
        <div class="title-block">
          <h2>留一点空间。</h2>
          <p class="sub">简单、离线，由你掌握 · v0.4.0</p>
        </div>
      </header>
      <div class="content">
        <p class="section-label">I · 外观与桌面</p>
        <div class="setting-group">
          <div class="setting-row" style="display:block">
            <span>纸张主题<small>道林暖白与深烘夜色两套显式 Token</small></span>
            <div class="chip-group">
              <button type="button" class="choice-chip ${!isDark ? 'active' : ''}" data-app-theme="light">道林暖白 (日间)</button>
              <button type="button" class="choice-chip ${isDark ? 'active' : ''}" data-app-theme="dark">深烘炭墨 (夜间)</button>
            </div>
          </div>
          <button class="setting-row full" data-scene="config">
            <span>桌面小部件配置<small>独立设置绑定分册、材质与三档密度</small></span>
            <span class="row-chevron">›</span>
          </button>
        </div>

        <p class="section-label">II · 分册与本地数据</p>
        <div class="setting-group">
          <button class="setting-row full" data-scene="manage">
            <span>书签分册管理 (${lists.length} 册)<small>新建、改名或删除自定义分册</small></span>
            <span class="row-chevron">›</span>
          </button>
          <div class="setting-row" style="display:block">
            <span>SAF 本地备份与恢复<small>导出 JSON 或按 ID 事务合并，同 ID 保留本机不覆盖</small></span>
            <div class="chip-group" style="margin-top:8px">
              <button type="button" class="paper-action-btn primary-action" data-backup-export>${icon('export')}<span>导出 JSON 备份</span></button>
              <button type="button" class="paper-action-btn" data-backup-import>${icon('import')}<span>从文件恢复预览</span></button>
            </div>
          </div>
        </div>

        <p class="section-label">III · 关于忘了没</p>
        <div class="setting-group">
          <div class="setting-row" style="display:block">
            <div class="version-head">
              <span>当前版本 v0.4.0</span>
              <span class="version-tag">${updateStatus === 'ready' ? 'READY · V0.4.1' : 'LOCAL FIRST'}</span>
            </div>
            <small>${updateStatus === 'ready' ? '已发现 v0.4.1 · 国内加速源已校验 SHA256' : '优先通过 GitHub Releases / jsDelivr 检查更新'}</small>
            <div class="chip-group" style="margin-top:8px">
              <button type="button" class="paper-action-btn primary-action" data-check-update>${icon('spark')}<span>${updateStatus === 'ready' ? '安装已校验更新包' : '检查更新'}</span></button>
            </div>
          </div>
        </div>
        <p class="helper">本地优先，无账号、无云同步、无后台轮询服务。</p>
      </div>
    `;
  }
}

function commitRename(listId, rawName) {
  const target = lists.find(l => l.id === listId);
  const nextName = (rawName || '').trim().slice(0, 24);
  if (!target || !nextName) {
    renamingListId = null;
    render();
    return;
  }
  if (lists.some(l => l.id !== listId && l.name.toLocaleLowerCase() === nextName.toLocaleLowerCase())) {
    toast('已存在同名分册，请换个名字。');
    return;
  }
  target.name = nextName;
  renamingListId = null;
  refresh();
  toast('分册已重命名。');
}

function renderConfig() {
  const preview = document.getElementById('config-preview');
  const meta = document.getElementById('config-meta');
  if (!preview) return;
  preview.classList.toggle('dark', config.theme === 'dark');
  preview.style.cssText = config.theme === 'dark'
    ? '--glass:rgba(44,37,30,.92);--glass-text:#f3e9dc;--glass-muted:#cbb9a4;--glass-primary:#e5b285;--primary:#e5b285;--on-primary:#261c14;--glass-edge:rgba(255,237,214,0.22)'
    : config.theme === 'light'
      ? '--glass:rgba(255,250,240,.86);--glass-text:#2a231d;--glass-muted:#6c5d4d;--glass-primary:#9a4b26;--primary:#9a4b26;--on-primary:#fffaf4;--glass-edge:rgba(255,255,255,0.78)'
      : '';
  preview.innerHTML = renderWidgetCard({
    listId: config.listId,
    size: config.size,
    density: config.density,
    isPreview: true
  });
  const densityName = { regular: '舒适（保留底栏）', compact: '紧凑（标题栏＋）', ultra: '超紧凑（36dp高密）' }[config.density];
  if (meta) {
    meta.textContent = `SPEC · ${config.size === 'large' ? '4×4 LARGE' : '4×2 STANDARD'} / ${densityName}`;
  }
}

function toast(message, undo) {
  clearTimeout(undoTimer);
  const host = document.getElementById('toast');
  host.innerHTML = `<div class="toast"><span>${escapeText(message)}</span>${undo ? '<button id="undo">撤销</button>' : ''}</div>`;
  if (undo) {
    document.getElementById('undo').onclick = () => {
      undo();
      host.innerHTML = '';
      clearTimeout(undoTimer);
    };
  }
  undoTimer = setTimeout(() => {
    host.innerHTML = '';
  }, 5000);
}

function refresh() {
  render();
  renderWidgets();
}

function positionEditorDialog() {
  const phoneBounds = phone.getBoundingClientRect();
  const bottomOffset = Math.max(8, window.innerHeight - phoneBounds.bottom + 8);
  Object.assign(editor.style, {
    position: 'fixed',
    left: `${phoneBounds.left + 8}px`,
    top: 'auto',
    bottom: `${bottomOffset}px`,
    right: 'auto',
    width: `${phoneBounds.width - 16}px`,
    maxHeight: `${Math.max(260, phoneBounds.height - 72)}px`
  });
}

function openEditor(listId = currentListId, itemId = null) {
  const item = items.find(entry => entry.id === itemId);
  lastFocus = document.activeElement;
  const targetListId = item ? item.listId : (listId || currentListId);
  let isPinned = item ? !!item.pinned : draft.pinned;
  let isBatch = item ? false : draft.batch;
  const initialTitle = item ? item.title : draft.title;
  const initialNote = item ? item.note : draft.note;
  const hasRestoredDraft = !item && (draft.title.trim().length > 0 || draft.note.trim().length > 0);

  function renderSheetContent() {
    const batchLines = isBatch
      ? initialTitle.split(/\r\n|\n|\r/).filter(line => line.trim().length > 0).length
      : 0;

    editor.innerHTML = `
      <div class="memo-perforation">
        <span>✦ TEAR-OFF MEMO · DOT GRID</span>
        <span>NO. ${pad2(item ? item.id : nextId)}</span>
      </div>
      <div class="sheet-title">
        <h2 id="editor-heading">${item ? '修订这页便签' : '想到，就落笔。'}</h2>
        <button type="button" class="close-sheet-btn" data-close>收起</button>
      </div>
      ${hasRestoredDraft ? `<div class="draft-banner"><span>已恢复上次未保存的草稿</span><button type="button" id="discard-draft">丢弃草稿</button></div>` : ''}
      <form id="entry-form">
        <div class="notepad-sheet">
          <textarea
            name="title"
            class="note-title-input"
            rows="${isBatch ? 4 : 2}"
            maxlength="${isBatch ? 2000 : 200}"
            placeholder="${isBatch ? '每行生成一条待办，最多 50 条…' : '写下要记住的小事…'}"
            required
          >${escapeText(initialTitle)}</textarea>
          <hr class="notepad-divider">
          <textarea
            name="note"
            class="note-body-input"
            rows="2"
            maxlength="2000"
            placeholder="附注或细节（可选）…"
          >${escapeText(initialNote)}</textarea>
          <div class="notepad-WaterMark" aria-hidden="true">
            <span id="char-counter">${initialTitle.length} / ${isBatch ? 2000 : 200}</span>
          </div>
        </div>

        <div class="meta-pills" aria-label="记录属性">
          <label class="meta-pill">
            <span>📒</span>
            <select name="listId" aria-label="放进哪份清单">
              ${lists.map(l => `<option value="${l.id}" ${l.id === targetListId ? 'selected' : ''}>${escapeText(l.name)}</option>`).join('')}
            </select>
          </label>
          <button type="button" class="meta-pill ${isPinned ? 'active' : ''}" id="pill-pin" aria-pressed="${isPinned}">
            <span>★</span><span>${isPinned ? '已置顶' : '置顶印记'}</span>
          </button>
          ${!item ? `<button type="button" class="meta-pill ${isBatch ? 'active' : ''}" id="pill-batch" aria-pressed="${isBatch}">
            <span>☰</span><span id="batch-label">${isBatch ? `批量 (${batchLines || 0}/50条)` : '按行拆条'}</span>
          </button>` : ''}
        </div>

        <p class="error" id="entry-error" role="alert"></p>
        <div class="inline">
          ${item
            ? `<button class="danger-btn" type="button" id="delete-item-btn">撕下删除</button>`
            : `<button class="secondary" type="button" id="save-continue-btn">入册并继续</button>`}
          <button class="primary" type="submit">${item ? '保存修订' : '入册保存'}</button>
        </div>
        <p class="helper">收起或返回会自动留存本地草稿；正式入册后清除。</p>
      </form>
    `;

    const form = document.getElementById('entry-form');
    const titleInput = form.elements.title;
    const noteInput = form.elements.note;

    titleInput.addEventListener('input', () => {
      if (!item) draft.title = titleInput.value;
      const counter = document.getElementById('char-counter');
      if (counter) {
        counter.textContent = `${titleInput.value.length} / ${isBatch ? 2000 : 200}`;
      }
      const batchLabel = document.getElementById('batch-label');
      if (isBatch && batchLabel) {
        const count = titleInput.value.split(/\r\n|\n|\r/).filter(l => l.trim().length > 0).length;
        batchLabel.textContent = `批量 (${count}/50条)`;
      }
    });

    noteInput.addEventListener('input', () => {
      if (!item) draft.note = noteInput.value;
    });

    document.getElementById('pill-pin').onclick = () => {
      isPinned = !isPinned;
      if (!item) draft.pinned = isPinned;
      const btn = document.getElementById('pill-pin');
      btn.classList.toggle('active', isPinned);
      btn.setAttribute('aria-pressed', String(isPinned));
      btn.querySelector('span:last-child').textContent = isPinned ? '已置顶' : '置顶印记';
    };

    const batchBtn = document.getElementById('pill-batch');
    if (batchBtn) {
      batchBtn.onclick = () => {
        isBatch = !isBatch;
        draft.batch = isBatch;
        draft.title = titleInput.value;
        draft.note = noteInput.value;
        openEditor(form.elements.listId.value, null);
      };
    }

    const discardBtn = document.getElementById('discard-draft');
    if (discardBtn) {
      discardBtn.onclick = () => {
        draft = { title: '', note: '', listId: targetListId, pinned: false, batch: false };
        openEditor(targetListId, null);
      };
    }

    const deleteBtn = document.getElementById('delete-item-btn');
    if (deleteBtn && item) {
      deleteBtn.onclick = () => {
        items = items.filter(entry => entry.id !== item.id);
        editor.close();
        refresh();
        toast('已撕下删除这条记录。');
      };
    }

    function commitSave(continueAfter = false) {
      const rawTitle = titleInput.value;
      const trimmed = rawTitle.trim();
      if (!trimmed) {
        document.getElementById('entry-error').textContent = '先写一点想记下来的事吧。';
        return;
      }
      const chosenListId = form.elements.listId.value;
      const noteVal = noteInput.value.trim();

      if (!item && isBatch) {
        const lines = rawTitle.split(/\r\n|\n|\r/).map(l => l.trim()).filter(Boolean);
        if (lines.length > 50) {
          document.getElementById('entry-error').textContent = `当前共 ${lines.length} 行，单次批量最多支持 50 条。`;
          return;
        }
        lines.forEach(line => {
          items.push({
            id: nextId++,
            listId: chosenListId,
            title: line.slice(0, 200),
            note: noteVal,
            pinned: isPinned,
            done: false
          });
        });
      } else if (item) {
        Object.assign(item, {
          listId: chosenListId,
          title: trimmed,
          note: noteVal,
          pinned: isPinned
        });
      } else {
        items.push({
          id: nextId++,
          listId: chosenListId,
          title: trimmed,
          note: noteVal,
          pinned: isPinned,
          done: false
        });
      }

      draft = { title: '', note: '', listId: chosenListId, pinned: false, batch: false };
      currentListId = chosenListId;
      completed = false;
      query = '';

      if (continueAfter) {
        refresh();
        openEditor(chosenListId, null);
        toast('已入册，继续写下一张。');
      } else {
        editor.close();
        if (scene !== 'todo') scene = 'todo';
        refresh();
        toast('已落笔入册，放下心来。');
      }
    }

    const saveContBtn = document.getElementById('save-continue-btn');
    if (saveContBtn) {
      saveContBtn.onclick = () => commitSave(true);
    }

    form.onsubmit = event => {
      event.preventDefault();
      commitSave(false);
    };
  }

  renderSheetContent();
  positionEditorDialog();

  phone.classList.add('editor-open');
  if (!editor.open) {
    editor.showModal();
  }
  const titleEl = editor.querySelector('[name="title"]');
  if (titleEl && !item) {
    titleEl.focus();
  }
}

window.addEventListener('resize', () => {
  if (editor.open) positionEditorDialog();
});

editor.addEventListener('click', event => {
  if (event.target === editor) {
    editor.close();
  }
});

document.addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  const data = button.dataset;

  if (data.scene) {
    if (data.scene === 'editor') {
      openEditor(currentListId);
      return;
    }
    clearTimeout(undoTimer);
    document.getElementById('toast').innerHTML = '';
    renamingListId = null;
    scene = data.scene;
    query = '';
    render();
  } else if (data.selectList) {
    currentListId = data.selectList;
    scene = 'todo';
    completed = false;
    query = '';
    render();
  } else if (data.complete) {
    const item = items.find(entry => entry.id === Number(data.complete));
    if (!item) return;
    item.done = !item.done;
    widgetUndo = item.done ? { itemId: item.id, listId: item.listId } : null;
    refresh();
    toast(item.done ? '已划掉一件小事。' : '已放回待办页。', () => {
      item.done = !item.done;
      widgetUndo = null;
      refresh();
    });
  } else if (data.widgetUndo !== undefined) {
    if (widgetUndo) {
      const item = items.find(entry => entry.id === widgetUndo.itemId);
      if (item) item.done = false;
      widgetUndo = null;
      refresh();
      toast('已从桌面卡片撤销完成。');
    }
  } else if (data.pin) {
    const item = items.find(entry => entry.id === Number(data.pin));
    if (!item) return;
    item.pinned = !item.pinned;
    refresh();
    toast(item.pinned ? '已盖上置顶印记。' : '已移除置顶印记。');
  } else if (data.edit) {
    openEditor(currentListId, Number(data.edit));
  } else if (data.add) {
    openEditor(data.add);
  } else if (data.openList) {
    currentListId = data.openList;
    scene = 'todo';
    completed = false;
    query = '';
    render();
  } else if (data.filter) {
    completed = data.filter === 'done';
    render();
  } else if (data.clearSearch !== undefined) {
    query = '';
    render();
  } else if ('close' in data) {
    editor.close();
  } else if (data.cfgKey) {
    config[data.cfgKey] = data.cfgVal;
    render();
    renderWidgets();
  } else if ('configSave' in data) {
    renderWidgets();
    toast('卡片配置已同步至右侧桌面。');
  } else if (data.renameList) {
    renamingListId = data.renameList;
    render();
  } else if (data.saveRename) {
    const inp = document.getElementById(`rename-input-${data.saveRename}`);
    commitRename(data.saveRename, inp ? inp.value : '');
  } else if (data.cancelRename !== undefined) {
    renamingListId = null;
    render();
  } else if (data.deleteList) {
    const target = lists.find(l => l.id === data.deleteList);
    if (!target || target.isDefault) return;
    const def = lists.find(l => l.isDefault) || lists[0];
    items.forEach(item => {
      if (item.listId === target.id) item.listId = def.id;
    });
    if (config.listId === target.id) config.listId = def.id;
    lists = lists.filter(l => l.id !== target.id);
    currentListId = def.id;
    refresh();
    toast(`已删除「${target.name}」，记录已迁回「${def.name}」。`);
  } else if (data.appTheme) {
    setTheme(data.appTheme === 'dark');
    render();
  } else if ('backupExport' in data) {
    toast(`已导出 ${lists.length} 册清单、${items.length} 条记录 (JSON)。`);
  } else if ('backupImport' in data) {
    toast('校验通过：预计合并 3 册清单、9 条记录，同 ID 保留本机。');
  } else if ('checkUpdate' in data) {
    if (updateStatus === 'idle') {
      updateStatus = 'ready';
      render();
      toast('已通过国内加速源发现 v0.4.1 并完成 SHA256 校验。');
    } else {
      toast('正在调起系统安装器确认安装 v0.4.1…');
    }
  }
});

document.addEventListener('input', event => {
  if (event.target.id === 'search') {
    query = event.target.value;
    const clearBtn = document.getElementById('clear-search');
    if (clearBtn) clearBtn.hidden = !query;
    renderRows();
  }
});

editor.addEventListener('close', () => {
  phone.classList.remove('editor-open');
  if (lastFocus?.isConnected) lastFocus.focus();
});

function setTheme(dark) {
  document.body.classList.toggle('dark', dark);
  document.getElementById('theme').innerHTML = dark ? '<span>☼</span> 日间预览' : '<span>☾</span> 夜间预览';
}

document.getElementById('theme').onclick = () => {
  setTheme(!document.body.classList.contains('dark'));
  render();
};

document.getElementById('wall').onclick = event => {
  const dark = document.getElementById('wallpaper').classList.toggle('dark-wall');
  event.currentTarget.textContent = dark ? '切换浅色壁纸' : '切换深色壁纸';
};

document.getElementById('material').onclick = event => {
  const native = document.getElementById('wallpaper').classList.toggle('native');
  event.currentTarget.setAttribute('aria-pressed', String(native));
  event.currentTarget.textContent = native ? '查看设计目标' : '查看原生近似';
  document.getElementById('material-note').textContent = native
    ? '原生近似 · 浅色 92% / 深色 90% 透光层 + 顶部细高光描边'
    : '设计目标 · 背景模糊 24px + 浅米透光层 + 顶部边缘高光';
};

document.getElementById('reset').onclick = () => {
  lists = structuredClone(seedLists);
  items = structuredClone(seedItems);
  nextId = 10;
  scene = 'todo';
  currentListId = 'todo';
  query = '';
  completed = false;
  widgetUndo = null;
  updateStatus = 'idle';
  renamingListId = null;
  draft = { title: '', note: '', listId: 'todo', pinned: false, batch: false };
  refresh();
  toast('已恢复初始演示数据。');
};

refresh();

/* ─────────────────────────────────────────────────────────────────
   RepoLens – client-side interactivity
   Vanilla JS; no build step required.
   ───────────────────────────────────────────────────────────────── */

// ── State ────────────────────────────────────────────────────────
const state = {
    repoData:      null,   // full ExploreResponse
    selectedPath:  null,   // currently highlighted path
    traversalMap:  new Map(), // path -> { order, depth }
    currentRepoUrl: '',
};

// ── DOM refs ─────────────────────────────────────────────────────
const $ = (id) => document.getElementById(id);
const exploreForm     = $('explore-form');
const repoUrlInput    = $('repo-url');
const traversalSelect = $('traversal-mode');
const exploreBtn      = $('explore-btn');
const errorBanner     = $('error-banner');
const treeBadge       = $('tree-badge');
const treePlaceholder = $('tree-placeholder');
const treeRoot        = $('tree-root');
const detailsPlaceholder = $('details-placeholder');
const detailsContent  = $('details-content');
const summarySection  = $('summary-section');
const summarizeBtn    = $('summarize-btn');
const summaryLoading  = $('summary-loading');
const summaryError    = $('summary-error');
const summaryText     = $('summary-text');

// ── Explore form submission ───────────────────────────────────────
exploreForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    await handleExplore();
});

async function handleExplore() {
    const repoUrl      = repoUrlInput.value.trim();
    const traversalMode = traversalSelect.value;

    if (!repoUrl) {
        showError('Please enter a GitHub repository URL.');
        return;
    }

    setLoading(true);
    hideError();

    try {
        const response = await fetch('/api/repository/explore', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ repo_url: repoUrl, traversal_mode: traversalMode }),
        });

        const payload = await safeJson(response);

        if (!response.ok) {
            throw new Error(payload?.detail ?? 'Could not load repository.');
        }

        state.repoData      = payload;
        state.currentRepoUrl = repoUrl;
        state.traversalMap  = buildTraversalMap(payload.traversal);
        state.selectedPath  = null;

        renderTree(payload);
        clearDetails();
    } catch (err) {
        showError(err instanceof Error ? err.message : 'Unexpected error while loading repository.');
        clearTree();
        clearDetails();
    } finally {
        setLoading(false);
    }
}

// ── Tree rendering ───────────────────────────────────────────────
function renderTree(data) {
    treeRoot.innerHTML = '';

    const rootItem = createTreeItem(data.tree, 0, true);
    treeRoot.appendChild(rootItem);

    treePlaceholder.hidden = true;
    treeRoot.hidden        = false;

    treeBadge.textContent = `${data.traversal_mode.toUpperCase()} • ${data.traversal.length} nodes`;
    treeBadge.hidden      = false;
}

function createTreeItem(node, depth, isRoot) {
    const li  = document.createElement('li');
    const row = document.createElement('div');
    row.className = 'tree-item-row';
    row.style.marginLeft = `${depth * 0.75}rem`;
    row.dataset.path = node.path;

    const isDir = node.type === 'directory';

    // Toggle or bullet
    if (isDir) {
        const toggle = document.createElement('button');
        toggle.type      = 'button';
        toggle.className = 'tree-toggle';
        toggle.textContent = '▸';
        toggle.setAttribute('aria-label', 'Expand folder');
        row.appendChild(toggle);
    } else {
        const bullet = document.createElement('span');
        bullet.className   = 'tree-bullet';
        bullet.textContent = '•';
        row.appendChild(bullet);
    }

    // Name button
    const nameBtn = document.createElement('button');
    nameBtn.type      = 'button';
    nameBtn.className = 'tree-name-btn';
    nameBtn.addEventListener('click', () => selectNode(node));

    const nameSpan = document.createElement('span');
    nameSpan.className   = 'tree-name';
    nameSpan.textContent = node.name;
    nameBtn.appendChild(nameSpan);

    if (!isDir && node.size !== null && node.size !== undefined) {
        const sizeSpan = document.createElement('span');
        sizeSpan.className   = 'tree-size';
        sizeSpan.textContent = formatBytes(node.size);
        nameBtn.appendChild(sizeSpan);
    }

    row.appendChild(nameBtn);
    li.appendChild(row);

    // Children (lazy-rendered on first expand)
    if (isDir && node.children && node.children.length > 0) {
        const childUl = document.createElement('ul');
        childUl.className = 'tree-children';
        childUl.hidden    = true;
        let rendered = false;

        const toggle = row.querySelector('.tree-toggle');

        function expandCollapse() {
            const willExpand = childUl.hidden;

            if (willExpand && !rendered) {
                const frag = document.createDocumentFragment();
                node.children.forEach(child => frag.appendChild(createTreeItem(child, depth + 1, false)));
                childUl.appendChild(frag);
                rendered = true;
            }

            childUl.hidden   = !willExpand;
            toggle.textContent = willExpand ? '▾' : '▸';
            toggle.setAttribute('aria-label', willExpand ? 'Collapse folder' : 'Expand folder');
        }

        toggle.addEventListener('click', expandCollapse);

        if (isRoot) expandCollapse(); // auto-expand root

        li.appendChild(childUl);
    }

    return li;
}

// ── Node selection ───────────────────────────────────────────────
function selectNode(node) {
    // Deselect previous
    document.querySelectorAll('.tree-item-row.is-selected')
            .forEach(el => el.classList.remove('is-selected'));

    // Highlight new
    const matchingRow = document.querySelector(`.tree-item-row[data-path="${CSS.escape(node.path)}"]`);
    if (matchingRow) matchingRow.classList.add('is-selected');

    state.selectedPath = node.path;

    // Populate details panel
    const repo       = state.repoData.repository;
    const traversal  = state.traversalMap.get(node.path);
    const isRoot     = node.path === state.repoData.tree.path;

    $('meta-name').textContent = isRoot ? repo.name : node.name;
    $('meta-path').textContent = isRoot ? '/' : node.path;
    $('meta-type').textContent = node.type.charAt(0).toUpperCase() + node.type.slice(1);
    $('meta-size').textContent = node.type === 'file' ? formatBytes(node.size) : 'N/A';
    $('meta-order').textContent = traversal ? traversal.order : 'N/A';
    $('meta-depth').textContent = traversal ? traversal.depth : 'N/A';

    const repoLink = $('meta-repo-link');
    repoLink.textContent = `${repo.owner}/${repo.name}`;
    repoLink.href        = repo.html_url;

    detailsPlaceholder.hidden = true;
    detailsContent.hidden     = false;

    // Summary section visibility
    const isFile = node.type === 'file';
    summarySection.hidden = !isFile;

    if (isFile) {
        resetSummary();
    }
}

// ── Summary ──────────────────────────────────────────────────────
summarizeBtn.addEventListener('click', async () => {
    if (!state.selectedPath || !state.currentRepoUrl) return;

    const filePath = state.selectedPath;
    const repoUrl  = state.currentRepoUrl;

    setSummarizing(true);
    hideSummaryResult();

    try {
        const response = await fetch('/api/repository/summary', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ repo_url: repoUrl, file_path: filePath }),
        });

        const payload = await safeJson(response);

        if (!response.ok) {
            showSummaryError(payload?.detail ?? 'Could not generate summary.');
            return;
        }

        summaryText.textContent = payload.summary;
        summaryText.hidden      = false;
    } catch (err) {
        showSummaryError(err instanceof Error ? err.message : 'Unexpected error generating summary.');
    } finally {
        setSummarizing(false);
    }
});

function resetSummary() {
    summaryLoading.hidden = true;
    summaryError.hidden   = true;
    summaryText.hidden    = true;
    summarizeBtn.disabled = false;
    summaryText.textContent = '';
}

function setSummarizing(loading) {
    summarizeBtn.disabled = loading;
    summaryLoading.hidden = !loading;
}

function hideSummaryResult() {
    summaryError.hidden = true;
    summaryText.hidden  = true;
}

function showSummaryError(message) {
    summaryError.textContent = message;
    summaryError.hidden      = false;
}

// ── Helpers ──────────────────────────────────────────────────────
function buildTraversalMap(entries) {
    const map = new Map();
    for (const entry of entries) {
        map.set(entry.path, { order: entry.order, depth: entry.depth });
    }
    return map;
}

function formatBytes(value) {
    if (value === null || value === undefined) return 'N/A';
    if (value < 1024) return `${value} B`;
    const units  = ['KB', 'MB', 'GB'];
    let amount   = value / 1024;
    let idx      = 0;
    while (amount >= 1024 && idx < units.length - 1) { amount /= 1024; idx++; }
    return `${amount.toFixed(1)} ${units[idx]}`;
}

async function safeJson(response) {
    try { return await response.json(); } catch { return null; }
}

function showError(message) {
    errorBanner.textContent = message;
    errorBanner.hidden      = false;
}

function hideError() {
    errorBanner.hidden = true;
}

function setLoading(loading) {
    exploreBtn.disabled   = loading;
    exploreBtn.textContent = loading ? 'Loading…' : 'Explore Repository';
}

function clearTree() {
    treeRoot.innerHTML    = '';
    treeRoot.hidden       = true;
    treePlaceholder.hidden = false;
    treeBadge.hidden      = true;
}

function clearDetails() {
    detailsContent.hidden     = true;
    detailsPlaceholder.hidden = false;
    summarySection.hidden     = true;
    resetSummary();
}

const $ = id => document.getElementById(id);
const state = { data:null, repoUrl:'', selected:null, flat:[], summaries:new Map(), previews:new Map() };
const icons = { directory:'▸', Java:'◆', JavaScript:'◇', TypeScript:'◇', Python:'●', Markdown:'▤', JSON:'{}', CSS:'#', HTML:'<>', Text:'·' };

init();
function init(){
  const savedTheme=localStorage.getItem('menyacode-theme');
  if(savedTheme==='dark'||(!savedTheme&&matchMedia('(prefers-color-scheme: dark)').matches)) document.body.classList.add('dark');
  $('theme-toggle').addEventListener('click',()=>{document.body.classList.toggle('dark');localStorage.setItem('menyacode-theme',document.body.classList.contains('dark')?'dark':'light')});
  $('explore-form').addEventListener('submit',e=>{e.preventDefault();explore()});
  document.querySelectorAll('.try-chip').forEach(b=>b.addEventListener('click',()=>{$('repo-url').value=b.dataset.repo;explore()}));
  $('tree-search').addEventListener('input',renderTree);
  $('type-filter').addEventListener('change',renderTree);
  $('traversal-mode').addEventListener('change',recalculateTraversal);
  $('expand-all').addEventListener('click',()=>document.querySelectorAll('.tree-children').forEach(x=>x.hidden=false));
  $('collapse-all').addEventListener('click',()=>document.querySelectorAll('.tree-children').forEach(x=>x.hidden=true));
  $('summarize-btn').addEventListener('click',summarize);
  $('copy-code').addEventListener('click',async()=>{await navigator.clipboard.writeText($('file-content').textContent);$('copy-code').textContent='Copied';setTimeout(()=>$('copy-code').textContent='Copy',1200)});
  document.querySelectorAll('.tab').forEach(t=>t.addEventListener('click',()=>selectTab(t.dataset.tab)));
  document.addEventListener('keydown',e=>{if(e.key==='/'&&!/INPUT|TEXTAREA/.test(document.activeElement.tagName)){e.preventDefault();$('tree-search').focus()}});
  renderRecent();
}

async function explore(){
  const repoUrl=$('repo-url').value.trim(), branch=$('branch').value.trim();
  if(!repoUrl)return showError('Enter a GitHub repository URL.');
  setLoading(true);hideError();
  try{
    const response=await fetch('/api/repository/explore',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({repo_url:repoUrl,traversal_mode:$('traversal-mode').value,branch})});
    const payload=await json(response);if(!response.ok)throw Error(payload?.detail||'Could not load repository.');
    state.data=payload;state.repoUrl=repoUrl;state.selected=null;state.flat=flatten(payload.tree);state.summaries.clear();state.previews.clear();
    renderRepository();renderTree();saveRecent(repoUrl,payload.repository.name);
    $('welcome').hidden=true;$('workspace').hidden=false;$('empty-detail').hidden=false;$('file-detail').hidden=true;
  }catch(e){showError(e.message)}finally{setLoading(false)}
}

function renderRepository(){
  const r=state.data.repository;
  $('repo-title').textContent=`${r.owner} / ${r.name}`;$('repo-branch').textContent=r.default_branch;
  $('repo-description').textContent=r.description||'No repository description provided.';$('repo-language').textContent=r.language?`● ${r.language}`:'';
  $('repo-stars').textContent=`★ ${number(r.stars)}`;$('repo-forks').textContent=`⑂ ${number(r.forks)}`;$('repo-link').href=r.html_url;
  $('repo-topics').replaceChildren(...(r.topics||[]).slice(0,6).map(topic=>el('span',{text:topic})));
}

function renderTree(){
  if(!state.data)return;const q=$('tree-search').value.trim().toLowerCase(),type=$('type-filter').value;
  $('tree-root').innerHTML='';let count=0;
  if(q){
    const matches=state.flat.filter(n=>(type==='all'||n.type===type)&&n.path.toLowerCase().includes(q));count=matches.length;
    matches.slice(0,300).forEach(n=>$('tree-root').appendChild(treeItem(n,0,false)));
  }else{$('tree-root').appendChild(treeItem(state.data.tree,0,true));count=state.flat.length}
  $('badge-count').textContent=`${state.flat.length.toLocaleString()} items`;$('tree-status').textContent=q?`${count} match${count===1?'':'es'}`:'';
}

function treeItem(node,depth,root){
  const li=el('li',{role:'treeitem'}),row=el('div',{class:'tree-item-row'+(node.path===state.selected?.path?' is-selected':'')});row.style.paddingLeft=`${Math.min(depth,20)*12}px`;
  const dir=node.type==='directory',toggle=el('button',{class:dir?'tree-toggle':'tree-bullet',text:dir?'▸':'·','aria-label':dir?'Expand folder':''});row.append(toggle);
  const language=detectLanguage(node.path),name=el('button',{class:'tree-name-btn'});name.append(el('span',{text:dir?'📁':fileIcon(language)}),el('span',{class:'tree-name',text:node.name}));
  if(!dir&&node.size!=null)name.append(el('span',{class:'tree-size',text:bytes(node.size)}));name.addEventListener('click',()=>selectNode(node));row.append(name);li.append(row);
  if(dir&&node.children?.length){const ul=el('ul',{class:'tree-children',role:'group'});ul.hidden=!root;node.children.forEach(c=>ul.append(treeItem(c,depth+1,false)));toggle.addEventListener('click',()=>{ul.hidden=!ul.hidden;toggle.textContent=ul.hidden?'▸':'▾'});li.append(ul)}
  return li;
}

async function selectNode(node){
  state.selected=node;renderTree();if(node.type==='directory')return;
  $('empty-detail').hidden=true;$('file-detail').hidden=false;$('file-name').textContent=node.name;$('file-path').textContent=node.path;
  const lang=detectLanguage(node.path);$('file-icon').textContent=fileIcon(lang);$('file-github-link').href=`${state.data.repository.html_url}/blob/${encodeURIComponent(state.data.repository.default_branch)}/${node.path.split('/').map(encodeURIComponent).join('/')}`;
  $('metadata-grid').replaceChildren(...[['Name',node.name],['Path',node.path],['Type','File'],['Size',bytes(node.size)],['Language',lang],['Traversal order',traversal(node.path)?.order??'—'],['Depth',traversal(node.path)?.depth??'—'],['Branch',state.data.repository.default_branch]].map(([k,v])=>{const d=el('div');d.append(el('dt',{text:k}),el('dd',{text:String(v)}));return d}));
  selectTab('preview');await preview(node);
}

async function preview(node){
  $('preview-language').textContent=detectLanguage(node.path);$('preview-size').textContent=bytes(node.size);$('preview-notice').hidden=true;$('file-content').textContent='';
  if(state.previews.has(node.path)){showPreview(state.previews.get(node.path));return}$('preview-loading').hidden=false;
  try{const response=await fetch('/api/repository/file',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({repo_url:state.repoUrl,file_path:node.path})});const p=await json(response);if(!response.ok)throw Error(p?.detail||'Preview unavailable.');state.previews.set(node.path,p);showPreview(p)}catch(e){$('preview-notice').textContent=e.message;$('preview-notice').hidden=false}finally{$('preview-loading').hidden=true}
}
function showPreview(p){$('file-content').textContent=withLineNumbers(p.content);if(p.truncated){$('preview-notice').textContent='This preview was truncated because the file is large.';$('preview-notice').hidden=false}}

async function summarize(){
  const path=state.selected?.path;if(!path)return;if(state.summaries.has(path)){showSummary(state.summaries.get(path));return}
  $('summary-loading').hidden=false;$('summary-error').hidden=true;$('summarize-btn').disabled=true;
  try{const response=await fetch('/api/repository/summary',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({repo_url:state.repoUrl,file_path:path})});const p=await json(response);if(!response.ok)throw Error(p?.detail||'Could not generate explanation.');state.summaries.set(path,p);showSummary(p)}catch(e){$('summary-error').textContent=e.message;$('summary-error').hidden=false}finally{$('summary-loading').hidden=true;$('summarize-btn').disabled=false}
}
function showSummary(p){$('summary-text').textContent=p.summary;$('summary-text').hidden=false;$('summary-meta').textContent=`${p.language} · ${p.analyzed_chars.toLocaleString()} characters · ${p.model}`;$('summary-meta').hidden=false}
function selectTab(name){document.querySelectorAll('.tab').forEach(t=>{const active=t.dataset.tab===name;t.classList.toggle('active',active);t.setAttribute('aria-selected',active)});document.querySelectorAll('.tab-panel').forEach(p=>p.hidden=p.id!==`tab-${name}`)}
function recalculateTraversal(){if(!state.data)return;const list=[],queue=[[state.data.tree,0]];if($('traversal-mode').value==='bfs'){while(queue.length){const [n,d]=queue.shift();list.push({path:n.path,depth:d,order:list.length+1});n.children?.forEach(c=>queue.push([c,d+1]))}}else{const walk=(n,d)=>{list.push({path:n.path,depth:d,order:list.length+1});n.children?.forEach(c=>walk(c,d+1))};walk(state.data.tree,0)}state.data.traversal=list;if(state.selected)selectNode(state.selected)}

function flatten(root){const out=[];(function walk(n){out.push(n);n.children?.forEach(walk)})(root);return out}function traversal(path){return state.data.traversal.find(x=>x.path===path)}
function detectLanguage(path){const ext=path.split('.').pop().toLowerCase(),map={java:'Java',js:'JavaScript',jsx:'JavaScript',ts:'TypeScript',tsx:'TypeScript',py:'Python',md:'Markdown',json:'JSON',css:'CSS',scss:'CSS',html:'HTML',go:'Go',rs:'Rust',rb:'Ruby',swift:'Swift',kt:'Kotlin',yml:'YAML',yaml:'YAML',xml:'XML',sql:'SQL',sh:'Shell'};return map[ext]||'Text'}
function fileIcon(lang){return icons[lang]||'·'}function bytes(v){if(v==null)return'—';if(v<1024)return`${v} B`;if(v<1048576)return`${(v/1024).toFixed(1)} KB`;return`${(v/1048576).toFixed(1)} MB`}function number(v){return(v||0).toLocaleString()}
function withLineNumbers(content){return content.split('\n').map((line,i)=>`${String(i+1).padStart(4)}  ${line}`).join('\n')}function el(tag,props={}){const n=document.createElement(tag);for(const[k,v]of Object.entries(props)){if(k==='text')n.textContent=v;else if(k==='class')n.className=v;else n.setAttribute(k,v)}return n}
async function json(r){try{return await r.json()}catch{return null}}function setLoading(v){$('explore-btn').disabled=v;$('explore-btn').textContent=v?'Analyzing…':'Analyze'}function showError(m){$('error-banner').textContent=m;$('error-banner').hidden=false}function hideError(){$('error-banner').hidden=true}
function saveRecent(url,name){const recent=JSON.parse(localStorage.getItem('menyacode-recent')||'[]').filter(x=>x.url!==url);recent.unshift({url,name});localStorage.setItem('menyacode-recent',JSON.stringify(recent.slice(0,5)))}
function renderRecent(){const recent=JSON.parse(localStorage.getItem('menyacode-recent')||'[]');if(!recent.length)return;$('recent-section').hidden=false;$('recent-repos').replaceChildren(...recent.map(x=>{const b=el('button',{text:x.name});b.onclick=()=>{$('repo-url').value=x.url;explore()};return b}))}

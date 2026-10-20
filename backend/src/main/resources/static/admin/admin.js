'use strict';

// Brauzer admin paneli: mövcud /api/admin/... endpoint-lərini işlədir.
// İstifadəçi mətni HƏMİŞƏ textContent ilə yazılır (innerHTML yox) - XSS olmasın.

const TOKEN_KEY = 'imtahan_admin_token';
const LETTERS = 'ABCDEF';
const MIN_OPTIONS = 2;
const MAX_OPTIONS = 6;
const MAX_IMAGE_SIZE = 1600;
const FREE_CATEGORY_COUNT = 3;

const $ = (id) => document.getElementById(id);

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key === 'value') node.value = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children) if (child != null) node.append(child);
  return node;
}

const session = {
  get() { try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; } },
  set(token) { try { sessionStorage.setItem(TOKEN_KEY, token); } catch { /* yalnız yaddaşda qalır */ } },
  clear() { try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* boş */ } },
};

const state = {
  token: session.get(),
  categories: [],
  current: null,
  questions: [],
  editingId: null,
  correct: 0,
  image: { file: null, url: null, previewUrl: null },
  results: [],
};

// ---------------------------------------------------------------- API

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function api(path, { method = 'GET', json, form } = {}) {
  const headers = {};
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  let body;
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  } else if (form) {
    body = form;
  }

  let res;
  try {
    res = await fetch(path, { method, headers, body });
  } catch {
    throw new ApiError(0, 'Serverə qoşulmaq mümkün olmadı. Serverin işlədiyini yoxlayın.');
  }

  if (res.status === 401 && path !== '/api/auth/admin') {
    logout('Sessiyanın vaxtı bitib. Yenidən daxil olun.');
    throw new ApiError(401, 'Sessiyanın vaxtı bitib.');
  }
  if (!res.ok) {
    let message = `Xəta baş verdi (kod ${res.status}).`;
    try {
      const data = await res.json();
      if (data && data.message) message = data.message;
    } catch { /* cavabda JSON yoxdur */ }
    throw new ApiError(res.status, message);
  }
  return res.status === 204 ? null : res.json();
}

const categoryName = (c) => c.name || `sınaq ${c.number}`;
const currentCategory = () => state.categories.find((c) => c.number === state.current);

// ---------------------------------------------------------------- Bildiriş və dialoq

let toastTimer;
function toast(message) {
  const node = $('toast');
  node.textContent = message;
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.hidden = true; }, 2600);
}

/** Təsdiq və ya mətn soruşan dialoq. Ləğv edilsə null, əks halda daxil edilən mətni ('' ola bilər) qaytarır. */
function ask({ title, text = '', label = null, value = '', confirm }) {
  const dialog = $('dialog');
  $('dialog-title').textContent = title;
  $('dialog-text').textContent = text;
  $('dialog-text').hidden = !text;
  $('dialog-field').hidden = !label;
  $('dialog-label').textContent = label || '';
  $('dialog-input').value = value;
  $('dialog-confirm').textContent = confirm;

  return new Promise((resolve) => {
    dialog.addEventListener('close', () => {
      resolve(dialog.returnValue === 'ok' ? $('dialog-input').value.trim() : null);
    }, { once: true });
    dialog.returnValue = '';
    dialog.showModal();
    if (label) $('dialog-input').select();
  });
}

// Enter formun BİRİNCİ düyməsini (Ləğv et) işə salardı - ona görə təsdiqi özümüz edirik
$('dialog-input').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    event.preventDefault();
    $('dialog').close('ok');
  }
});

// ---------------------------------------------------------------- Giriş / çıxış

$('login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.submitter;
  const password = $('login-password').value;
  $('login-error').textContent = '';
  button.disabled = true;
  try {
    const { token } = await api('/api/auth/admin', { method: 'POST', json: { password } });
    state.token = token;
    session.set(token);
    await showApp();
  } catch (err) {
    $('login-error').textContent = err.message;
    $('login-password').select();
  } finally {
    button.disabled = false;
  }
});

$('logout').addEventListener('click', () => logout());

function logout(message = '') {
  state.token = null;
  session.clear();
  $('app').hidden = true;
  $('login').hidden = false;
  $('login-error').textContent = message;
  $('login-password').value = '';
  $('login-password').focus();
}

async function showApp() {
  $('login').hidden = true;
  $('app').hidden = false;
  await loadCategories();
  const first = state.categories[0];
  if (first) await selectCategory(first.number);
  else showView('category');
}

// ---------------------------------------------------------------- Görünüşlər

function showView(name) {
  for (const view of ['category', 'results', 'accounts']) {
    $(`view-${view}`).hidden = view !== name;
  }
  document.querySelectorAll('.nav-item').forEach((item) => {
    const active = name === 'category'
      ? item.dataset.number === String(state.current)
      : item.dataset.view === name;
    item.setAttribute('aria-current', String(active));
  });
}

document.querySelectorAll('.nav-item[data-view]').forEach((item) => {
  item.addEventListener('click', () => {
    showView(item.dataset.view);
    if (item.dataset.view === 'results') loadResults();
  });
});

// ---------------------------------------------------------------- Sınaqlar

async function loadCategories() {
  state.categories = await api('/api/categories');
  renderCategories();
}

function renderCategories() {
  $('category-list').replaceChildren(...state.categories.map((c) => el('li', {},
    el('button', {
      class: 'nav-item',
      type: 'button',
      'data-number': c.number,
      'aria-current': String(c.number === state.current),
      onclick: () => selectCategory(c.number),
    },
      el('span', { class: 'nav-num', text: String(c.number) }),
      el('span', { class: 'nav-name', text: categoryName(c) }),
      c.number > FREE_CATEGORY_COUNT ? el('span', { class: 'nav-lock', text: 'ödənişli' }) : null,
    ),
  )));
}

async function selectCategory(number) {
  state.current = number;
  resetEditor();
  showView('category');
  renderCategoryHead();
  await loadQuestions();
}

function renderCategoryHead() {
  const category = currentCategory();
  if (!category) return;
  $('category-title').textContent = categoryName(category);
  const count = state.questions.length;
  const kind = category.number > FREE_CATEGORY_COUNT ? 'ödənişli sınaq' : 'pulsuz sınaq';
  $('category-meta').textContent = `${count} sual, ${kind}`;
}

$('add-category').addEventListener('click', async () => {
  const name = await ask({
    title: 'Yeni sınaq',
    text: 'Ad verməsəniz, sınaq nömrəsi ilə adlanacaq.',
    label: 'Sınağın adı (istəyə bağlı)',
    confirm: 'Sınaq yarat',
  });
  if (name === null) return;
  try {
    const created = await api('/api/admin/categories', { method: 'POST', json: { name: name || null } });
    await loadCategories();
    await selectCategory(created.number);
    toast('Sınaq yaradıldı.');
  } catch (err) {
    if (err.status !== 401) toast(err.message);
  }
});

$('rename-category').addEventListener('click', async () => {
  const category = currentCategory();
  if (!category) return;
  const name = await ask({
    title: 'Sınağın adını dəyiş',
    text: 'Boş buraxsanız, sınaq yenidən nömrəsi ilə göstəriləcək.',
    label: 'Yeni ad',
    value: category.name || '',
    confirm: 'Adı saxla',
  });
  if (name === null) return;
  try {
    await api(`/api/admin/categories/${category.number}`, { method: 'PATCH', json: { name: name || null } });
    await loadCategories();
    renderCategoryHead();
    toast('Ad saxlanıldı.');
  } catch (err) {
    if (err.status !== 401) toast(err.message);
  }
});

$('delete-category').addEventListener('click', async () => {
  const category = currentCategory();
  if (!category) return;
  const answer = await ask({
    title: 'Sınağı sil',
    text: `"${categoryName(category)}" sınağı və içindəki ${state.questions.length} sual həmişəlik silinəcək.`,
    confirm: 'Sınağı sil',
  });
  if (answer === null) return;
  try {
    await api(`/api/admin/categories/${category.number}`, { method: 'DELETE' });
    await loadCategories();
    const first = state.categories[0];
    if (first) await selectCategory(first.number);
    toast('Sınaq silindi.');
  } catch (err) {
    if (err.status !== 401) toast(err.message);
  }
});

// ---------------------------------------------------------------- Suallar siyahısı

async function loadQuestions() {
  if (state.current == null) return;
  const number = state.current;
  try {
    const questions = await api(`/api/admin/categories/${number}/questions`);
    if (number !== state.current) return;   // bu arada başqa sınaq seçilib
    state.questions = questions;
  } catch (err) {
    if (err.status !== 401) toast(err.message);
    return;
  }
  renderCategoryHead();
  renderQuestions();
}

function renderQuestions() {
  $('question-empty').hidden = state.questions.length > 0;
  $('question-list').hidden = state.questions.length === 0;
  $('question-list').replaceChildren(...state.questions.map((q, index) => el('li', {
    class: `question${q.id === state.editingId ? ' is-editing' : ''}`,
  },
    el('span', { class: 'question-num', text: `${index + 1}.` }),
    el('div', { class: 'question-body' },
      el('p', { class: 'question-text', text: q.text }),
      q.imageUrl ? el('img', { class: 'question-img', src: q.imageUrl, alt: '', loading: 'lazy' }) : null,
      el('ul', { class: 'question-options' }, ...q.options.map((option, i) => {
        const correct = i === q.correctAnswerIndex;
        return el('li', { class: correct ? 'is-correct' : null },
          el('span', { class: `bubble${correct ? ' is-correct' : ''}`, text: LETTERS[i], 'aria-label': correct ? `${LETTERS[i]}, düzgün cavab` : LETTERS[i] }),
          el('span', { text: option }),
        );
      })),
    ),
    el('div', { class: 'question-actions' },
      el('button', { class: 'btn btn-quiet btn-small', type: 'button', text: 'Düzəlt', onclick: () => startEdit(q, index + 1) }),
      el('button', { class: 'btn btn-danger-quiet btn-small', type: 'button', text: 'Sil', onclick: () => deleteQuestion(q, index + 1) }),
    ),
  )));
}

async function deleteQuestion(q, position) {
  const answer = await ask({
    title: `Sual №${position} silinsin?`,
    text: q.text,
    confirm: 'Sualı sil',
  });
  if (answer === null) return;
  try {
    await api(`/api/admin/questions/${q.id}`, { method: 'DELETE' });
    if (state.editingId === q.id) resetEditor();
    await loadQuestions();
    toast('Sual silindi.');
  } catch (err) {
    if (err.status !== 401) toast(err.message);
  }
}

// ---------------------------------------------------------------- Sual redaktoru

const optionRows = $('option-rows');

function optionValues() {
  return [...optionRows.querySelectorAll('input')].map((input) => input.value);
}

function renderOptions(values) {
  optionRows.replaceChildren(...values.map((value, i) => el('div', { class: 'option-row' },
    el('button', {
      class: 'bubble',
      type: 'button',
      text: LETTERS[i],
      'aria-label': `${LETTERS[i]} variantını düzgün cavab seç`,
      onclick: () => { state.correct = i; refreshBubbles(); },
    }),
    el('input', {
      type: 'text',
      value,
      'aria-label': `${LETTERS[i]} variantı`,
      onkeydown: (event) => {
        if (event.key === 'Enter' && !event.ctrlKey && !event.metaKey) {
          event.preventDefault();
          const inputs = optionRows.querySelectorAll('input');
          inputs[i + 1]?.focus();
        }
      },
    }),
    values.length > MIN_OPTIONS ? el('button', {
      class: 'btn btn-quiet remove-option',
      type: 'button',
      text: 'Sil',
      'aria-label': `${LETTERS[i]} variantını sil`,
      onclick: () => {
        const current = optionValues();
        current.splice(i, 1);
        if (state.correct === i) state.correct = 0;
        else if (state.correct > i) state.correct -= 1;
        renderOptions(current);
      },
    }) : null,
  )));
  $('add-option').hidden = values.length >= MAX_OPTIONS;
  refreshBubbles();
}

function refreshBubbles() {
  optionRows.querySelectorAll('.bubble').forEach((bubble, i) => {
    const correct = i === state.correct;
    bubble.classList.toggle('is-correct', correct);
    bubble.setAttribute('aria-pressed', String(correct));
  });
}

$('add-option').addEventListener('click', () => {
  const values = optionValues();
  if (values.length >= MAX_OPTIONS) return;
  renderOptions([...values, '']);
  optionRows.querySelectorAll('input')[values.length].focus();
});

function resetEditor() {
  state.editingId = null;
  state.correct = 0;
  $('q-text').value = '';
  renderOptions(['', '', '', '']);
  setImage({ file: null, url: null });
  $('question-error').textContent = '';
  $('editing-note').hidden = true;
  $('cancel-edit').hidden = true;
  $('save-question').textContent = 'Sualı əlavə et';
  document.querySelectorAll('.question.is-editing').forEach((item) => item.classList.remove('is-editing'));
}

function startEdit(q, position) {
  setMode('single');
  state.editingId = q.id;
  state.correct = q.correctAnswerIndex;
  $('q-text').value = q.text;
  renderOptions(q.options);
  setImage({ file: null, url: q.imageUrl });
  $('question-error').textContent = '';
  $('editing-number').textContent = String(position);
  $('editing-note').hidden = false;
  $('cancel-edit').hidden = false;
  $('save-question').textContent = 'Dəyişikliyi saxla';
  renderQuestions();
  $('question-form').scrollIntoView({ behavior: 'smooth', block: 'center' });
  $('q-text').focus({ preventScroll: true });
}

$('cancel-edit').addEventListener('click', () => resetEditor());

$('question-form').addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    $('question-form').requestSubmit();
  }
});

$('question-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const error = $('question-error');
  error.textContent = '';

  const text = $('q-text').value.trim();
  // Boş variantlar atılır, düzgün cavabın sırası buna uyğun yenidən hesablanır
  const filled = optionValues()
    .map((value, i) => ({ value: value.trim(), i }))
    .filter((option) => option.value);
  const correct = filled.findIndex((option) => option.i === state.correct);

  if (!text) { error.textContent = 'Sualın mətnini yazın.'; $('q-text').focus(); return; }
  if (filled.length < MIN_OPTIONS) { error.textContent = 'Ən azı 2 variant yazın.'; return; }
  if (correct === -1) { error.textContent = 'Düzgün cavab kimi seçilən variant boşdur. Onu yazın və ya başqa hərfi seçin.'; return; }

  const button = $('save-question');
  const label = button.textContent;
  button.disabled = true;
  button.textContent = 'Saxlanılır…';
  try {
    let imageUrl = state.image.url;
    if (state.image.file) imageUrl = await uploadImage(state.image.file);
    const payload = { text, options: filled.map((option) => option.value), correctAnswerIndex: correct, imageUrl };

    if (state.editingId) {
      await api(`/api/admin/questions/${state.editingId}`, { method: 'PUT', json: payload });
      toast('Dəyişiklik saxlanıldı.');
    } else {
      await api(`/api/admin/categories/${state.current}/questions`, { method: 'POST', json: [payload] });
      toast('Sual əlavə edildi.');
    }
    resetEditor();
    await loadQuestions();
    $('q-text').focus();
  } catch (err) {
    if (err.status !== 401) error.textContent = err.message;
    button.textContent = label;
  } finally {
    button.disabled = false;
  }
});

// ---------------------------------------------------------------- Şəkil

function setImage({ file, url }) {
  if (state.image.previewUrl) URL.revokeObjectURL(state.image.previewUrl);
  const previewUrl = file ? URL.createObjectURL(file) : null;
  state.image = { file, url, previewUrl };

  const src = previewUrl || url;
  $('dropzone-empty').hidden = Boolean(src);
  $('dropzone-preview').hidden = !src;
  if (src) $('image-preview').src = src;
  else $('image-preview').removeAttribute('src');
}

function acceptImageFile(file) {
  if (!file || !file.type.startsWith('image/')) {
    $('question-error').textContent = 'Seçilən fayl şəkil deyil.';
    return;
  }
  $('question-error').textContent = '';
  setImage({ file, url: null });
}

/** Şəkli maks. 1600px-ə kiçildib JPEG kimi yükləyir (server 5 MB-dan böyüyünü qəbul etmir). */
async function uploadImage(file) {
  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error('Şəkil oxuna bilmədi. Başqa fayl seçin.');
  }
  const scale = Math.min(1, MAX_IMAGE_SIZE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff';   // şəffaf PNG JPEG-də qara görünməsin
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();

  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((result) => (result ? resolve(result) : reject(new Error('Şəkil hazırlana bilmədi.'))), 'image/jpeg', 0.85);
  });
  const form = new FormData();
  form.append('file', blob, 'image.jpg');
  const { url } = await api('/api/admin/images', { method: 'POST', form });
  return url;
}

const dropzone = $('dropzone');

dropzone.addEventListener('click', (event) => {
  if (event.target.closest('#remove-image')) return;
  $('image-input').click();
});
dropzone.addEventListener('keydown', (event) => {
  if (event.target === dropzone && (event.key === 'Enter' || event.key === ' ')) {
    event.preventDefault();
    $('image-input').click();
  }
});
$('image-input').addEventListener('change', (event) => {
  acceptImageFile(event.target.files[0]);
  event.target.value = '';
});
$('remove-image').addEventListener('click', () => setImage({ file: null, url: null }));

dropzone.addEventListener('dragover', (event) => { event.preventDefault(); dropzone.classList.add('is-over'); });
dropzone.addEventListener('dragleave', () => dropzone.classList.remove('is-over'));
dropzone.addEventListener('drop', (event) => {
  event.preventDefault();
  dropzone.classList.remove('is-over');
  acceptImageFile(event.dataTransfer.files[0]);
});

// Ctrl+V ilə ekran görüntüsünü və ya kopyalanmış şəkli birbaşa yapışdırmaq
document.addEventListener('paste', (event) => {
  if ($('view-category').hidden || $('question-form').hidden) return;
  const file = [...event.clipboardData.files].find((f) => f.type.startsWith('image/'));
  if (!file) return;   // adi mətn yapışdırılır - toxunmuruq
  event.preventDefault();
  acceptImageFile(file);
  toast('Şəkil əlavə edildi.');
});

// ---------------------------------------------------------------- Tək / toplu rejim

function setMode(mode) {
  const single = mode === 'single';
  $('question-form').hidden = !single;
  $('bulk-form').hidden = single;
  $('mode-single').setAttribute('aria-selected', String(single));
  $('mode-bulk').setAttribute('aria-selected', String(!single));
}

$('mode-single').addEventListener('click', () => setMode('single'));
$('mode-bulk').addEventListener('click', () => { setMode('bulk'); $('bulk-text').focus(); });

/** Format: sual boş sətirlə ayrılır, 1-ci sətir mətn, qalanları variant, düzgünün əvvəlində "*". */
function parseBulk(raw) {
  const blocks = raw.replace(/\r/g, '').split(/\n\s*\n/).map((b) => b.trim()).filter(Boolean);
  const questions = [];
  let skipped = 0;
  for (const block of blocks) {
    const lines = block.split('\n').map((line) => line.trim()).filter(Boolean);
    const options = lines.slice(1);
    if (options.length < MIN_OPTIONS || options.length > MAX_OPTIONS) { skipped += 1; continue; }
    const correct = Math.max(0, options.findIndex((line) => line.startsWith('*')));
    questions.push({
      text: lines[0],
      options: options.map((line) => line.replace(/^\*/, '').trim()),
      correctAnswerIndex: correct,
    });
  }
  return { questions, skipped };
}

function updateBulkSummary() {
  const { questions, skipped } = parseBulk($('bulk-text').value);
  const button = $('bulk-save');
  button.disabled = questions.length === 0;
  button.textContent = questions.length ? `${questions.length} sualı yüklə` : 'Sualları yüklə';
  $('bulk-count').textContent = skipped
    ? `${skipped} blok nəzərə alınmayacaq: hər sualın 2-6 variantı olmalıdır.`
    : '';
}

$('bulk-text').addEventListener('input', updateBulkSummary);

$('bulk-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const { questions } = parseBulk($('bulk-text').value);
  if (!questions.length) return;
  const button = $('bulk-save');
  button.disabled = true;
  $('bulk-error').textContent = '';
  try {
    await api(`/api/admin/categories/${state.current}/questions`, { method: 'POST', json: questions });
    $('bulk-text').value = '';
    updateBulkSummary();
    await loadQuestions();
    toast(`${questions.length} sual əlavə edildi.`);
  } catch (err) {
    if (err.status !== 401) $('bulk-error').textContent = err.message;
    button.disabled = false;
  }
});

// ---------------------------------------------------------------- Nəticələr

const pad = (n) => String(n).padStart(2, '0');
function formatDate(ms) {
  const d = new Date(ms);
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadResults() {
  try {
    state.results = await api('/api/admin/results');
  } catch (err) {
    if (err.status !== 401) toast(err.message);
    return;
  }
  renderResults();
}

function renderResults() {
  const query = $('results-filter').value.trim().toLowerCase();
  const digits = query.replace(/\D/g, '');
  const rows = state.results.filter((r) => !query
    || `${r.firstName} ${r.lastName}`.toLowerCase().includes(query)
    || (digits && r.phone.includes(digits)));

  $('results-empty').hidden = state.results.length > 0;
  $('results-body').replaceChildren(...rows.map((r) => {
    const passed = r.total > 0 && r.score * 2 >= r.total;
    return el('tr', {},
      el('td', { text: `${r.firstName} ${r.lastName}` }),
      el('td', { text: `+${r.phone}` }),
      el('td', { text: r.categoryName || `sınaq ${r.categoryNumber}` }),
      el('td', { class: 'num' }, el('span', { class: `score ${passed ? 'pass' : 'fail'}`, text: `${r.score} / ${r.total}` })),
      el('td', { text: formatDate(r.createdAt) }),
    );
  }));
}

$('results-filter').addEventListener('input', renderResults);
$('results-refresh').addEventListener('click', loadResults);

// ---------------------------------------------------------------- Ödəniş və cihaz

function bindAccountForm(formId, inputId, path, success) {
  const form = $(formId);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const result = form.querySelector('.form-result');
    const phone = $(inputId).value.trim();
    result.className = 'form-result';
    if (phone.replace(/\D/g, '').length < 9) {
      result.classList.add('err');
      result.textContent = 'Telefon nömrəsini tam yazın, məsələn 050 123 45 67.';
      return;
    }
    const button = event.submitter;
    button.disabled = true;
    try {
      await api(path, { method: 'POST', json: { phone } });
      result.classList.add('ok');
      result.textContent = success(phone);
      $(inputId).value = '';
    } catch (err) {
      if (err.status !== 401) {
        result.classList.add('err');
        result.textContent = err.message;
      }
    } finally {
      button.disabled = false;
    }
  });
}

bindAccountForm('unlock-form', 'unlock-phone', '/api/admin/unlock', (p) => `${p} nömrəsi üçün bütün sınaqlar açıldı.`);
bindAccountForm('reset-form', 'reset-phone', '/api/admin/reset-device', (p) => `${p} nömrəsinin cihazı sıfırlandı.`);

// ---------------------------------------------------------------- Başlanğıc

resetEditor();
if (state.token) {
  showApp().catch(() => { /* 401 olarsa logout() artıq giriş ekranını göstərib */ });
} else {
  logout();
}

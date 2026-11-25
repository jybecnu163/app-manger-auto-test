async function api(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();
    let data;
    try {
        data = text ? JSON.parse(text) : null;
    } catch (e) {
        data = text;
    }
    if (!response.ok) {
        const detail = data && data.message ? data.message : String(data || text).substring(0, 200);
        throw new Error('HTTP ' + response.status + '：' + detail);
    }
    return data;
}

function jsonBody(value) {
    return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) };
}

function fmt(value) {
    return value === null || value === undefined ? '' : JSON.stringify(value, null, 2);
}

function show(id, value, error) {
    document.getElementById(id).textContent = (error ? '错误：' + error.message : fmt(value));
}

function numberOrZero(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
}

function renderProducts(data) {
    const items = data.items || [];
    document.getElementById('listMeta').textContent =
        '共 ' + (data.total || 0) + ' 条，当前第 ' + (data.page || 1) + ' 页，每页 ' + (data.size || 10) + ' 条。';
    document.getElementById('listBody').innerHTML = items.length
        ? items.map(item =>
            '<tr><td>' + item.id + '</td>'
            + '<td>' + escapeHtml(item.name) + '</td>'
            + '<td>' + escapeHtml(item.price) + '</td>'
            + '<td>' + item.stock + '</td>'
            + '<td>' + escapeHtml(item.category) + '</td>'
            + '<td>' + escapeHtml(item.status) + '</td>'
            + '<td><button class="secondary" data-id="' + item.id + '">查看详情</button></td></tr>').join('')
        : '<tr><td colspan="7" class="muted">暂无数据</td></tr>';
    document.querySelectorAll('#listBody button[data-id]').forEach(button => {
        button.onclick = () => {
            document.getElementById('productId').value = button.dataset.id;
            getProduct();
        };
    });
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

async function loadList() {
    const params = new URLSearchParams();
    const keyword = document.getElementById('listKeyword').value.trim();
    if (keyword) params.set('keyword', keyword);
    params.set('page', document.getElementById('listPage').value || '1');
    params.set('size', document.getElementById('listSize').value || '10');
    try {
        renderProducts(await api('/api/products?' + params.toString()));
    } catch (e) {
        document.getElementById('listMeta').textContent = e.message;
    }
}

async function advancedSearch() {
    const body = {
        keyword: document.getElementById('searchKeyword').value.trim(),
        category: document.getElementById('searchCategory').value,
        status: document.getElementById('searchStatus').value || null,
        minPrice: document.getElementById('searchMinPrice').value,
        maxPrice: document.getElementById('searchMaxPrice').value,
        page: numberOrZero(document.getElementById('searchPage').value) || 1,
        size: numberOrZero(document.getElementById('searchSize').value) || 10
    };
    if (!body.minPrice) delete body.minPrice;
    if (!body.maxPrice) delete body.maxPrice;
    try {
        renderProducts(await api('/api/products/search', jsonBody(body)));
    } catch (e) {
        document.getElementById('listMeta').textContent = e.message;
    }
}

async function createProduct() {
    const body = {
        name: document.getElementById('newName').value.trim(),
        price: numberOrZero(document.getElementById('newPrice').value),
        stock: numberOrZero(document.getElementById('newStock').value),
        category: document.getElementById('newCategory').value.trim(),
        status: document.getElementById('newStatus').value
    };
    try {
        show('createResult', await api('/api/products', jsonBody(body)));
        loadCategories();
        loadList();
    } catch (e) {
        show('createResult', null, e);
    }
}

async function getProduct() {
    try {
        show('productResult', await api('/api/products/' + document.getElementById('productId').value));
    } catch (e) {
        show('productResult', null, e);
    }
}

async function getStock() {
    try {
        show('productResult', await api('/api/products/' + document.getElementById('productId').value + '/stock'));
    } catch (e) {
        show('productResult', null, e);
    }
}

async function adjustStock() {
    const id = document.getElementById('productId').value;
    const body = {
        change: numberOrZero(document.getElementById('stockChange').value),
        reason: document.getElementById('stockReason').value.trim()
    };
    try {
        show('productResult', await api('/api/products/' + id + '/stock/adjust', jsonBody(body)));
        loadList();
    } catch (e) {
        show('productResult', null, e);
    }
}

async function loadCategories() {
    try {
        const categories = await api('/api/products/categories');
        const options = categories.map(item => '<option value="' + escapeHtml(item) + '">' + escapeHtml(item) + '</option>').join('');
        document.getElementById('searchCategory').innerHTML = '<option value="">全部分类</option>' + options;
        document.getElementById('categoryOptions').innerHTML = options;
    } catch (e) {
        console.error(e);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('loadList').onclick = loadList;
    document.getElementById('advancedSearch').onclick = advancedSearch;
    document.getElementById('createProduct').onclick = createProduct;
    document.getElementById('getProduct').onclick = getProduct;
    document.getElementById('getStock').onclick = getStock;
    document.getElementById('adjustStock').onclick = adjustStock;
    loadCategories();
    loadList();
});

/**
 * THỰC ĐƠN & BÀN (chủ quán / quản lý): 3 thẻ Món / Danh mục / Bàn.
 * Cả 3 đều dựng từ crudPage() - VÍ DỤ TÁI SỬ DỤNG phía app: mỗi mục chỉ cần mô tả trường form và cách hiển thị 1 thẻ,
 * còn danh sách / thêm / sửa / xóa / xác nhận / báo lỗi đã có sẵn (giống CrudController + CrudService ở backend).
 */
import { h, mount } from '../core/dom.js';
import { money } from '../core/format.js';
import { categoryRepo, productRepo, tableRepo } from '../data/repositories.js';
import { pill, productThumb, toolbar } from '../ui/components.js';
import { crudPage } from '../ui/crud-page.js';

const RESOURCES = {
  products: () => crudPage({
    singular: 'món', repo: productRepo, emptyIcon: 'coffee',
    defaults: { available: true },
    // Trường "Danh mục" là ô chọn: danh sách lựa chọn tải từ máy chủ mỗi lần mở form
    fields: async () => [
      { name: 'name', label: 'Tên món', required: true },
      { name: 'categoryId', label: 'Danh mục', type: 'select', required: true,
        options: (await categoryRepo.list()).map((c) => ({ value: c.id, label: `${c.icon || ''} ${c.name}` })) },
      { name: 'price', label: 'Giá (đồng)', type: 'number', required: true, min: 0 },
      { name: 'description', label: 'Mô tả', type: 'textarea', hint: 'Trợ lý AI dùng mô tả này để tư vấn món cho khách' },
      { name: 'imageUrl', label: 'Link ảnh', hint: 'vd /img/latte.jpg hoặc https://...' },
      { name: 'available', label: 'Đang bán', type: 'switch', hint: 'Tắt khi hết nguyên liệu: món hiện "Tạm hết", không gọi được' },
    ],
    item: (p) => ({
      title: p.name,
      sub: `${p.categoryName} · ${money(p.price)}`,
      thumb: productThumb(p),
      meta: !p.available ? pill('Tạm hết', 'warn') : p.bestSeller ? pill('Bán chạy', 'info') : null,
    }),
  }),

  categories: () => crudPage({
    singular: 'danh mục', repo: categoryRepo, emptyIcon: 'book',
    defaults: { sortOrder: 0 },
    fields: [
      { name: 'name', label: 'Tên danh mục', required: true },
      { name: 'icon', label: 'Biểu tượng (emoji)', placeholder: '☕' },
      { name: 'description', label: 'Mô tả' },
      { name: 'sortOrder', label: 'Thứ tự hiển thị', type: 'number', hint: 'Số nhỏ hiện trước' },
    ],
    item: (c) => ({ title: `${c.icon || ''} ${c.name}`.trim(), sub: c.description }),
  }),

  tables: () => crudPage({
    singular: 'bàn', repo: tableRepo, emptyIcon: 'grid',
    defaults: { seats: 4, active: true },
    fields: [
      { name: 'name', label: 'Tên bàn', required: true, placeholder: 'Bàn 07' },
      { name: 'area', label: 'Khu vực', placeholder: 'Trong nhà / Sân vườn', hint: 'Sơ đồ bàn nhóm các bàn theo khu vực' },
      { name: 'seats', label: 'Số chỗ', type: 'number', required: true, min: 1, max: 50 },
      { name: 'active', label: 'Đang sử dụng', type: 'switch', hint: 'Tắt để ẩn bàn khỏi sơ đồ (đang sửa, cất đi...)' },
    ],
    item: (t) => ({ title: t.name, sub: `${t.area || 'Chưa đặt khu vực'} · ${t.seats} chỗ`, meta: t.active ? null : pill('Tạm ngưng', 'warn') }),
  }),
};

export function render(ctx) {
  let current = RESOURCES[ctx.query.tab] ? ctx.query.tab : 'products';
  const box = h('div');
  const seg = h('div', { class: 'st-seg' });
  const draw = () => {
    mount(seg, [['products', 'Món'], ['categories', 'Danh mục'], ['tables', 'Bàn']].map(([k, label]) =>
      h('button', { class: 'st-seg-btn' + (k === current ? ' on' : ''), type: 'button', 'data-tab': k,
        onclick: () => { current = k; draw(); } }, label)));
    mount(box, RESOURCES[current]());
  };
  draw();
  return h('section', { 'aria-label': 'Thực đơn và bàn' }, toolbar('Thực đơn & bàn'), seg, box);
}

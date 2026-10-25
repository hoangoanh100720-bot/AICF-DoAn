/**
 * TRANG QUẢN LÝ CRUD TỔNG QUÁT - đối xứng với CrudController / CrudService ở backend.
 *
 * Chỉ cần mô tả tài nguyên, hàm này dựng sẵn: danh sách thẻ + nút Thêm + bấm 1 thẻ để Sửa (trong hộp sửa có Xóa),
 * có trạng thái đang tải / rỗng / lỗi + thử lại, hỏi lại trước khi xóa.
 *
 *   crudPage({
 *     singular: 'món', repo: productRepo, emptyIcon: 'coffee',
 *     fields: [{ name: 'name', label: 'Tên', required: true }, ...],
 *     item: (p) => ({ title: p.name, sub: '...', thumb: productThumb(p), meta: pill('Tạm hết') }),
 *   })
 * Dùng cho 3 mục: Món, Danh mục, Bàn (xem pages/manage.js).
 */
import { h, mount } from '../core/dom.js';
import { confirmBox, emptyState, errorState, formSheet, spinner, toast, toastError } from './components.js';
import { icon } from './icons.js';

/**
 * @param {object} cfg
 * @param {string} cfg.singular       tên 1 bản ghi, vd 'món'
 * @param {object} cfg.repo           repository có list/create/update/remove (createCrudRepository)
 * @param {object[]|() => Promise<object[]>} cfg.fields  cấu hình trường form (tải động được, vd danh sách danh mục)
 * @param {(row) => {title, sub, thumb, meta}} cfg.item  cách hiển thị 1 thẻ
 * @param {(row) => object} [cfg.toForm]  dữ liệu thẻ -> giá trị form khi sửa (mặc định giữ nguyên)
 * @param {string} [cfg.emptyIcon]
 * @param {object} [cfg.defaults]     giá trị mặc định khi thêm mới
 */
export function crudPage(cfg) {
  const { singular, repo, item, toForm = (r) => r, emptyIcon = 'grid', defaults = {} } = cfg;
  const box = h('div', null, spinner());

  const openForm = async (row) => {
    let fields;
    try {
      fields = typeof cfg.fields === 'function' ? await cfg.fields() : cfg.fields;
    } catch (err) {
      return toastError(err);
    }
    const sh = formSheet({
      title: row ? 'Sửa ' + singular : 'Thêm ' + singular,
      fields,
      initial: row ? toForm(row) : defaults,
      submitText: row ? 'Lưu thay đổi' : 'Thêm ' + singular,
      success: row ? 'Đã lưu thay đổi' : 'Đã thêm ' + singular,
      onSubmit: async (data) => {
        if (row) await repo.update(row.id, data);
        else await repo.create(data);
        load();
      },
      extra: row ? h('button', {
        class: 'btn btn-ghost danger btn-block', type: 'button', 'data-act': 'delete',
        onclick: async () => {
          if (!(await confirmBox({ title: 'Xóa ' + singular, message: 'Xóa ' + singular + ' "' + item(row).title + '"? Không thể hoàn tác.', okText: 'Xóa', danger: true }))) return;
          try {
            await repo.remove(row.id);
            sh.close();
            toast('Đã xóa ' + singular, 'ok');
            load();
          } catch (err) {
            toastError(err);   // vd 409: danh mục còn món
          }
        },
      }, icon('trash', 16), 'Xóa ' + singular) : null,
    });
  };

  async function load() {
    let rows;
    try {
      rows = await repo.list();
    } catch (err) {
      mount(box, errorState(err, load));
      return;
    }
    const addBtn = h('button', { class: 'btn btn-primary btn-sm', type: 'button', 'data-act': 'add', onclick: () => openForm(null) },
      icon('plus', 16), 'Thêm ' + singular);
    if (!rows.length) {
      mount(box, emptyState(emptyIcon, 'Chưa có ' + singular + ' nào', null, addBtn));
      return;
    }
    mount(box,
      h('div', { class: 'st-toolbar' }, h('span', { class: 'note' }, rows.length + ' ' + singular), addBtn),
      h('div', { class: 'st-cards' }, rows.map((row) => {
        const v = item(row);
        return h('button', { class: 'st-card click crud-item', type: 'button', 'data-row': v.title, onclick: () => openForm(row) },
          h('div', { class: 'crud-row' },
            v.thumb ? h('div', { class: 'crud-thumb' }, v.thumb) : null,
            h('div', { class: 'grow' }, h('strong', null, v.title), v.sub ? h('p', { class: 'note' }, v.sub) : null, v.meta || null),
            icon('chevron', 18)));
      })));
  }
  load();
  return box;
}

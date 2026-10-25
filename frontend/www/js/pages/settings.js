/**
 * CÀI ĐẶT QUÁN & AI (chỉ chủ quán):
 *  - Thông tin quán: PUT /api/settings/shop (tên quán hiện trên thanh trên + màn đăng nhập; giờ mở cửa -> nhãn "Đang mở cửa")
 *  - Trợ lý AI: chọn Gemini / Claude, model, khóa API (PUT /api/settings/ai). Máy chủ không bao giờ trả lại khóa,
 *    chỉ cho biết đã nhập hay chưa; để trống ô khóa = giữ khóa cũ.
 */
import { h, mount } from '../core/dom.js';
import { appState } from '../core/store.js';
import { settingsRepo } from '../data/repositories.js';
import { errorState, formFields, readForm, showFieldErrors, spinner, toast, toolbar } from '../ui/components.js';

const SHOP_FIELDS = [
  { name: 'name', label: 'Tên quán', required: true },
  { name: 'address', label: 'Địa chỉ' },
  { name: 'hours', label: 'Giờ mở cửa', placeholder: '7:00 - 22:00 hằng ngày', hint: 'Dạng "7:00 - 22:00" để app tự hiện "Đang mở cửa"' },
];

export function render() {
  const box = h('div', null, spinner());
  async function load() {
    let shop;
    let ai;
    try {
      [shop, ai] = await Promise.all([settingsRepo.shop(), settingsRepo.ai()]);
    } catch (err) {
      mount(box, errorState(err, load));
      return;
    }
    const aiFields = [
      { name: 'provider', label: 'Dịch vụ AI', type: 'select', required: true, options: [{ value: 'gemini', label: 'Google Gemini' }, { value: 'claude', label: 'Anthropic Claude' }] },
      { name: 'geminiModel', label: 'Model Gemini' },
      { name: 'geminiApiKey', label: 'Khóa API Gemini', type: 'password', hint: ai.geminiKeySet ? '✓ Đã có khóa - để trống để giữ nguyên' : 'Chưa có khóa (lấy tại aistudio.google.com)' },
      { name: 'claudeModel', label: 'Model Claude' },
      { name: 'claudeApiKey', label: 'Khóa API Claude', type: 'password', hint: ai.claudeKeySet ? '✓ Đã có khóa - để trống để giữ nguyên' : 'Chưa có khóa (lấy tại console.anthropic.com)' },
    ];
    mount(box,
      section('Thông tin quán', SHOP_FIELDS, shop, async (data) => {
        appState.set({ shop: await settingsRepo.updateShop(data) });
        toast('Đã lưu thông tin quán', 'ok');
      }),
      h('div', { class: 'st-alert ' + (shop.aiMode === 'LLM' ? 'ok' : 'warn') }, h('span', null, shop.aiMode === 'LLM'
        ? 'Trợ lý đang dùng AI thật.'
        : 'Trợ lý đang ở chế độ cơ bản (trả lời theo từ khóa). Nhập khóa API để dùng AI thật.')),
      section('Trợ lý AI', aiFields, ai, async (data) => {
        await settingsRepo.updateAi(data);
        toast('Đã lưu cấu hình AI', 'ok');
        load();
      }),
      h('div', { class: 'st-alert ' + (shop.firebaseEnabled ? 'ok' : 'info') }, h('span', null, shop.firebaseEnabled
        ? 'Firebase đã kết nối: đăng nhập Google và thông báo đẩy đang bật.'
        : 'Máy chủ chưa cấu hình Firebase (xem README, mục Firebase). App vẫn chạy, thông báo chỉ có trong app.')));
  }
  load();
  return h('section', { 'aria-label': 'Cài đặt' }, toolbar('Cài đặt quán & AI'), box);
}

/** 1 thẻ form cài đặt (dùng lại cách vẽ ô nhập của formSheet). */
function section(title, fields, initial, onSave) {
  const btn = h('button', { class: 'btn btn-primary btn-block', type: 'submit' }, 'Lưu');
  const err = h('p', { class: 'form-error', role: 'alert' });
  const form = h('form', { class: 'card st-form', novalidate: true }, h('h3', null, title),
    formFields(fields, initial), err, btn);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    if (!form.reportValidity()) return;
    btn.disabled = true;
    try {
      await onSave(readForm(form, fields));
    } catch (ex) {
      if (!showFieldErrors(form, ex)) err.textContent = ex.message;
    } finally {
      btn.disabled = false;
    }
  });
  return form;
}

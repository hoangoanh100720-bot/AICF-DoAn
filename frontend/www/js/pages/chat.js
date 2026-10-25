/**
 * TRỢ LÝ AI (giống mục "Hỏi đáp" của Trợ lý trong app nhân viên AICF): bong bóng chat, câu hỏi gợi ý,
 * ô nhập tự giãn, Enter để gửi. AI chỉ ĐỌC dữ liệu thật của quán (thực đơn, bàn, bill đang mở;
 * doanh thu chỉ với chủ quán / quản lý).
 *   GET /api/chat/status · GET /api/chat · POST /api/chat · DELETE /api/chat
 */
import { h, mount } from '../core/dom.js';
import { chatRepo } from '../data/repositories.js';
import { confirmBox, pill, toastError, toolbar } from '../ui/components.js';
import { icon } from '../ui/icons.js';

const WELCOME = 'Xin chào! Mình là Trợ lý của quán. Bạn có thể hỏi về thực đơn, bàn trống, bill đang chờ pha'
  + ' (chủ quán / quản lý hỏi thêm được doanh thu, món bán chạy). Mình chỉ đọc dữ liệu thật trong app, không sửa gì.';

export function render() {
  let busy = false;
  const log = h('div', { class: 'as-log', role: 'log', 'aria-live': 'polite', 'aria-label': 'Cuộc trò chuyện' });
  const chips = h('div', { class: 'as-chips', 'aria-label': 'Câu hỏi gợi ý' });
  const mode = h('span', null, pill('…'));
  const input = h('textarea', { class: 'as-input', rows: 1, maxlength: 2000, placeholder: 'Hỏi Trợ lý: "Còn bàn trống không?"', 'aria-label': 'Câu hỏi cho Trợ lý' });
  const sendBtn = h('button', { class: 'btn btn-primary as-send', type: 'button', 'aria-label': 'Gửi' }, icon('arrow', 20));

  const bubble = (role, text, meta) => {
    const b = h('div', { class: 'as-msg ' + (role === 'USER' ? 'user' : 'bot'), 'data-role': role },
      h('div', { class: 'as-text' }, text), meta ? h('div', { class: 'as-meta' }, meta) : null);
    log.append(b);
    log.scrollTop = log.scrollHeight;
    return b;
  };
  const paintChips = (list) => mount(chips, (list || []).slice(0, 5).map((s) => h('button', { class: 'as-chip', type: 'button', onclick: () => send(s) }, s)));
  const paintMode = (m, provider) => mount(mode, m === 'LLM' ? pill('✨ AI thật' + (provider ? ' · ' + provider : ''), 'ok') : pill('Chế độ cơ bản', 'warn'));
  const autoSize = () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 120) + 'px'; };

  async function send(text) {
    const q = String(text ?? input.value).trim();
    if (!q || busy) return;
    busy = true;
    input.value = '';
    autoSize();
    sendBtn.disabled = true;
    bubble('USER', q);
    const wait = bubble('ASSISTANT', 'Đang xem số liệu…');
    wait.classList.add('wait');
    try {
      const r = await chatRepo.ask(q);
      wait.remove();
      bubble('ASSISTANT', r.reply, (r.mode === 'LLM' ? 'AI thật' : 'Chế độ cơ bản') + (r.notice ? ' · ' + r.notice : ''));
      paintChips(r.suggestions);
      paintMode(r.mode);
    } catch (err) {
      wait.remove();
      bubble('ASSISTANT', 'Mình chưa trả lời được: ' + err.message);
    } finally {
      busy = false;
      sendBtn.disabled = false;
      input.focus();
    }
  }

  input.addEventListener('input', autoSize);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } });
  sendBtn.addEventListener('click', () => send());
  const clear = h('button', { class: 'st-link', type: 'button', 'data-act': 'clear-chat', onclick: async () => {
    if (!(await confirmBox({ title: 'Xóa cuộc trò chuyện', message: 'Xóa toàn bộ lịch sử trò chuyện với Trợ lý?', okText: 'Xóa', danger: true }))) return;
    try {
      await chatRepo.clear();
      mount(log);
      bubble('ASSISTANT', WELCOME);
    } catch (err) { toastError(err); }
  } }, 'Xóa cuộc trò chuyện');

  Promise.all([chatRepo.status(), chatRepo.history(50)]).then(([status, history]) => {
    paintMode(status.mode, status.provider);
    paintChips(status.suggestions);
    if (!history.length) bubble('ASSISTANT', WELCOME);
    history.forEach((m) => bubble(m.role, m.content, m.role === 'ASSISTANT' && m.mode === 'LLM' ? 'AI thật' : ''));
  }).catch((err) => bubble('ASSISTANT', 'Không tải được Trợ lý: ' + err.message));

  return h('section', { class: 'as-page', 'aria-label': 'Trợ lý AI' },
    toolbar('Trợ lý AI', mode),
    h('div', { class: 'as-chat' }, log, chips, h('div', { class: 'as-compose' }, input, sendBtn), clear));
}

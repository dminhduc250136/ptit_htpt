'use client';

/**
 * QuickReplyChips — 3 suggested-question chips shown in empty-state of ChatPanel.
 *
 * Phase 22 Plan 05, decision D-11. Suggestions in Vietnamese, sourced from
 * 22-CONTEXT.md `<specifics>`.
 */
import styles from './QuickReplyChips.module.css';

const CHIPS = [
  'Tư vấn laptop tầm 20 triệu cho sinh viên?',
  'iPhone 15 Pro Max còn hàng không?',
  'So sánh chuột Logitech G Pro X và Razer DeathAdder V3',
];

interface Props {
  onPick: (text: string) => void;
}

export default function QuickReplyChips({ onPick }: Props) {
  return (
    <div className={styles.row} role="group" aria-label="Câu hỏi gợi ý">
      {CHIPS.map((c) => (
        <button
          key={c}
          type="button"
          className={styles.chip}
          onClick={() => onPick(c)}
        >
          {c}
        </button>
      ))}
    </div>
  );
}

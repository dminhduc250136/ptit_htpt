'use client';

/**
 * MessageBubble — role-aware chat bubble.
 *
 * Phase 22 Plan 05.
 *
 * Threat T-22-01 (XSS via assistant content) mitigated by routing assistant text
 * through `react-markdown` with default safe config (no `rehype-raw`,
 * no raw-HTML injection). `<img>` is rendered as `null` (no remote images),
 * `<a>` opens with `rel="noopener noreferrer" target="_blank"`.
 */
import ReactMarkdown from 'react-markdown';
import styles from './MessageBubble.module.css';

interface Props {
  role: 'user' | 'assistant';
  content: string;
  isStreaming?: boolean;
}

export default function MessageBubble({ role, content, isStreaming }: Props) {
  return (
    <div
      className={`${styles.bubble} ${role === 'user' ? styles.user : styles.assistant}`}
      role="article"
      data-role={role}
    >
      {role === 'assistant' ? (
        <div className={styles.markdown}>
          <ReactMarkdown
            components={{
              a: ({ href, children }) => (
                <a href={href} rel="noopener noreferrer" target="_blank">
                  {children}
                </a>
              ),
              img: () => null,
            }}
          >
            {content || ' '}
          </ReactMarkdown>
          {isStreaming && <span className={styles.cursor} aria-hidden="true">▍</span>}
        </div>
      ) : (
        <span className={styles.userText}>{content}</span>
      )}
    </div>
  );
}

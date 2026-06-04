import { useEffect, useId, useRef } from 'react';
import { logoMarkSrc, portalMeta } from '../config/portalMeta';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function PortalAboutDialog({ open, onClose }: Props) {
  const titleId = useId();
  const descId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  const { developer } = portalMeta;

  return (
    <div className="portal-dialog-backdrop" onClick={onClose} role="presentation">
      <div
        className="portal-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          ref={closeRef}
          type="button"
          className="portal-dialog-close"
          aria-label="Close"
          onClick={onClose}
        >
          ×
        </button>

        <header className="portal-dialog-header">
          <img src={logoMarkSrc} alt="" width={56} height={56} decoding="async" />
          <div>
            <h2 id={titleId}>{portalMeta.name}</h2>
            <p className="portal-dialog-tagline">{portalMeta.tagline}</p>
          </div>
        </header>

        <p id={descId} className="portal-dialog-lead">
          {portalMeta.description}
        </p>
        <p className="portal-dialog-meta">{portalMeta.versionLabel}</p>

        <section className="portal-dialog-section">
          <h3>Developer</h3>
          <p>
            <strong>{developer.name}</strong> — {developer.role}
          </p>
          <p className="portal-dialog-handles">
            GitHub {developer.githubHandle}
            {developer.twitterHandle ? ` · X ${developer.twitterHandle}` : null}
          </p>
        </section>

        <nav className="portal-dialog-links" aria-label="External links">
          <a href={portalMeta.docsUrl} target="_blank" rel="noopener noreferrer">
            Product documentation
          </a>
          <a href={portalMeta.guideUrl} target="_blank" rel="noopener noreferrer">
            Product guide
          </a>
          <a href={portalMeta.repoUrl} target="_blank" rel="noopener noreferrer">
            Source repository
          </a>
          <a href={developer.profileUrl} target="_blank" rel="noopener noreferrer">
            {developer.name} on GitHub
          </a>
        </nav>
      </div>
    </div>
  );
}

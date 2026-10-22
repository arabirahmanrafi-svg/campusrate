import { Link } from 'react-router-dom';
import usePageMeta from '../usePageMeta.js';

export default function NotFoundPage() {
  usePageMeta({ title: 'Page not found', noindex: true });
  return (
    <div className="narrow center">
      <h1>Page not found</h1>
      <p className="muted">
        That page doesn't exist. <Link to="/">Go back home</Link>
      </p>
    </div>
  );
}

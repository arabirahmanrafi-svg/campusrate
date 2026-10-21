import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="narrow center">
      <h1>Page not found</h1>
      <p className="muted">
        That page doesn't exist. <Link to="/">Go back home</Link>
      </p>
    </div>
  );
}

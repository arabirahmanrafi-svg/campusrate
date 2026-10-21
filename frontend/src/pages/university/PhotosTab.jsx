import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage, getPhotos } from '../../api.js';
import { useAuth } from '../../AuthContext.jsx';
import { PhotoGrid, PhotoUploader } from '../../components/Common.jsx';

/** Real campus photos from students, not marketing pictures. */
export default function PhotosTab({ university }) {
  const { user } = useAuth();
  const [photos, setPhotos] = useState(null);
  const [error, setError] = useState('');

  function load() {
    getPhotos(university.id)
      .then(setPhotos)
      .catch((e) => setError(errorMessage(e)));
  }

  useEffect(load, [university.id]);

  return (
    <>
      {user ? (
        <PhotoUploader
          universityId={university.id}
          label="Share a photo of campus life"
          onUploaded={(photo) => photo.status === 'APPROVED' && load()}
        />
      ) : (
        <p className="muted">
          <Link to="/login">Log in</Link> to share photos.
        </p>
      )}
      {error && <div className="alert">{error}</div>}
      {photos && photos.length === 0 && <div className="card empty">No photos yet.</div>}
      <PhotoGrid photos={photos} canReport={Boolean(user)} />
    </>
  );
}

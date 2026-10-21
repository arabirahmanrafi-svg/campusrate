// All calls to the Spring Boot backend, using the browser's built-in fetch().

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api';
const TOKEN_KEY = 'campusrate_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

/** Full address of an uploaded photo (the API returns paths like "/files/abc.jpg"). */
export function fileUrl(path) {
  return BASE_URL + path;
}

/** Thrown when the server answers with an error (status 400, 401, 404, ...). */
export class ApiError extends Error {
  constructor(status, data) {
    super(data?.message || `Request failed with status ${status}`);
    this.status = status;
    this.data = data;
  }
}

/**
 * Sends a request and returns the JSON response.
 * Adds the login token when we have one, and turns error responses into ApiError.
 * Pass `form` (a FormData) instead of `body` to upload files.
 */
async function request(method, path, { params, body, form } = {}) {
  const url = new URL(BASE_URL + path);
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
  });

  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  // For FormData the browser sets the multipart Content-Type (with its boundary) itself.
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(url, {
    method,
    headers,
    body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });

  // 204 No Content (e.g. after DELETE) has no body to read.
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { message: text };
  }

  if (!response.ok) throw new ApiError(response.status, data);
  return data;
}

/** Turns any error into a sentence we can show the user. */
export function errorMessage(error) {
  if (error instanceof ApiError) {
    if (error.data?.message) return error.data.message;
    if (error.status === 401) return 'Please log in first.';
    if (error.status === 403) return "You don't have permission to do that.";
    return 'Something went wrong. Please try again.';
  }
  // fetch() itself failed: the server is down or unreachable.
  return "Can't reach the server. Is the backend running?";
}

/** Field-level validation errors from the backend, e.g. { body: "must be ..." }. */
export function fieldErrors(error) {
  return (error instanceof ApiError && error.data?.fields) || {};
}

// ---- Universities, reviews, rankings ----

export const searchUniversities = (query) => request('GET', '/universities', { params: { query } });
export const getUniversity = (id) => request('GET', `/universities/${id}`);
export const getReviews = (id, { sort, page, international, country } = {}) =>
  request('GET', `/universities/${id}/reviews`, { params: { sort, page, international, country } });
export const getRankings = (category, country) => request('GET', '/rankings', { params: { category, country } });
export const getCountries = () => request('GET', '/countries');
export const createReview = (review) => request('POST', '/reviews', { body: review });
export const updateReview = (id, review) => request('PUT', `/reviews/${id}`, { body: review });
export const deleteReview = (id) => request('DELETE', `/reviews/${id}`);
export const toggleHelpful = (id) => request('POST', `/reviews/${id}/helpful`);
export const getMyReviews = () => request('GET', '/me/reviews');

// ---- Insights ----

export const getInsights = (id) => request('GET', `/universities/${id}/insights`);
export const askReviews = (id, q) => request('GET', `/universities/${id}/ask`, { params: { q } });

// ---- Places: dorms, dining, courses, jobs, clubs ----

export const getPlaceTypes = () => request('GET', '/place-types');
export const getPlaces = (universityId, type) => request('GET', `/universities/${universityId}/places`, { params: { type } });
export const suggestPlace = (universityId, place) => request('POST', `/universities/${universityId}/places`, { body: place });
export const getPlace = (id) => request('GET', `/places/${id}`);
export const reviewPlace = (id, review) => request('POST', `/places/${id}/reviews`, { body: review });
export const getDiningToday = (universityId) => request('GET', `/universities/${universityId}/dining/today`);

// ---- International ----

export const getInternational = (id) => request('GET', `/universities/${id}/international`);
export const reportCosts = (id, costs) => request('POST', `/universities/${id}/costs`, { body: costs });
export const suggestLink = (id, link) => request('POST', `/universities/${id}/links`, { body: link });

// ---- Q&A ----

export const getQuestions = (universityId) => request('GET', `/universities/${universityId}/questions`);
export const askQuestion = (universityId, question) =>
  request('POST', `/universities/${universityId}/questions`, { body: question });
export const getQuestion = (id) => request('GET', `/questions/${id}`);
export const answerQuestion = (id, answer) => request('POST', `/questions/${id}/answers`, { body: answer });
export const voteAnswer = (id) => request('POST', `/answers/${id}/vote`);

// ---- Photos ----

export const getPhotos = (universityId) => request('GET', `/universities/${universityId}/photos`);
export const reportPhoto = (id) => request('POST', `/photos/${id}/report`);
export function uploadPhoto(universityId, file, { caption, placeId, reviewId } = {}) {
  const form = new FormData();
  form.append('file', file);
  if (caption) form.append('caption', caption);
  if (placeId) form.append('placeId', placeId);
  if (reviewId) form.append('reviewId', reviewId);
  return request('POST', `/universities/${universityId}/photos`, { form });
}

// ---- Following and notifications ----

export const getFollow = (id) => request('GET', `/universities/${id}/follow`);
export const toggleFollow = (id) => request('POST', `/universities/${id}/follow`);
export const getNotifications = () => request('GET', '/me/notifications');
export const markNotificationsRead = () => request('POST', '/me/notifications/read');

// ---- Discovery ----

export const compareUniversities = (ids) => request('GET', '/compare', { params: { ids: ids.join(',') } });
export const getTrending = () => request('GET', '/explore/trending');
export const getValueForMoney = () => request('GET', '/explore/value');
export const findFit = (answers) => request('POST', '/fit', { body: answers });

// ---- Account ----

export const register = (form) => request('POST', '/auth/register', { body: form });
export const login = (form) => request('POST', '/auth/login', { body: form });
export const getMe = () => request('GET', '/auth/me');
export const updateProfile = (profile) => request('PUT', '/me/profile', { body: profile });

// ---- Admin ----

export const getAdminStats = () => request('GET', '/admin/stats');
export const getFlaggedReviews = () => request('GET', '/admin/flags');
export const getPendingItems = () => request('GET', '/admin/pending');
export const moderate = (kind, id, action) => request('POST', `/admin/${kind}/${id}/${action}`);
export const sendDigest = () => request('POST', '/admin/digest');

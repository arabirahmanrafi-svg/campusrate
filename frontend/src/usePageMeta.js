import { useEffect } from 'react';

// The public address of the site. Used for canonical links so Google treats
// "?tab=..." variants of a page as the same page.
export const SITE_URL = (import.meta.env.VITE_SITE_URL || 'https://campusrate.vercel.app').replace(/\/$/, '');

const SITE_NAME = 'CampusRate';
const DEFAULT_TITLE = 'CampusRate · Honest student reviews of universities';
const DEFAULT_DESCRIPTION =
  'Honest student reviews of universities: academics, campus life, dorms, dining, courses, campus jobs and support for international students.';

function setMeta(attr, key, content) {
  let tag = document.head.querySelector(`meta[${attr}="${key}"]`);
  if (content == null) {
    tag?.remove();
    return;
  }
  if (!tag) {
    tag = document.createElement('meta');
    tag.setAttribute(attr, key);
    document.head.appendChild(tag);
  }
  tag.setAttribute('content', content);
}

function setCanonical(href) {
  let link = document.head.querySelector('link[rel="canonical"]');
  if (!link) {
    link = document.createElement('link');
    link.setAttribute('rel', 'canonical');
    document.head.appendChild(link);
  }
  link.setAttribute('href', href);
}

/** Keeps descriptions within the ~155 characters search engines show. */
function clip(text, max = 155) {
  if (!text) return text;
  const clean = text.replace(/\s+/g, ' ').trim();
  return clean.length <= max ? clean : `${clean.slice(0, max - 1).trimEnd()}…`;
}

/**
 * Sets the browser tab title, the search-result description, the canonical URL and
 * social-preview tags for the current page.
 *
 *   usePageMeta({ title: 'University of Michigan reviews', description: '...' })
 *   usePageMeta({ title: 'Log in', noindex: true })   // keep private pages out of Google
 *
 * Pass title: null while data is still loading; the defaults stay in place until then.
 */
export default function usePageMeta({ title, description, noindex = false } = {}) {
  useEffect(() => {
    const fullTitle = title ? `${title} | ${SITE_NAME}` : DEFAULT_TITLE;
    const desc = clip(description) || DEFAULT_DESCRIPTION;
    const url = SITE_URL + window.location.pathname;

    document.title = fullTitle;
    setMeta('name', 'description', desc);
    setMeta('name', 'robots', noindex ? 'noindex, nofollow' : null);
    setMeta('property', 'og:title', fullTitle);
    setMeta('property', 'og:description', desc);
    setMeta('property', 'og:url', url);
    setCanonical(url);
  }, [title, description, noindex]);
}

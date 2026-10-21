-- Approximate campus coordinates (used for the map on each university page).
UPDATE universities SET latitude = 42.2506, longitude = -83.6238 WHERE name = 'Eastern Michigan University';
UPDATE universities SET latitude = 42.2780, longitude = -83.7382 WHERE name = 'University of Michigan';
UPDATE universities SET latitude = 42.7018, longitude = -84.4822 WHERE name = 'Michigan State University';
UPDATE universities SET latitude = 42.3591, longitude = -83.0665 WHERE name = 'Wayne State University';
UPDATE universities SET latitude = 42.2834, longitude = -85.6145 WHERE name = 'Western Michigan University';
UPDATE universities SET latitude = 43.5906, longitude = -84.7747 WHERE name = 'Central Michigan University';
UPDATE universities SET latitude = 42.3601, longitude = -71.0942 WHERE name = 'Massachusetts Institute of Technology';
UPDATE universities SET latitude = 42.3770, longitude = -71.1167 WHERE name = 'Harvard University';
UPDATE universities SET latitude = 37.4275, longitude = -122.1697 WHERE name = 'Stanford University';
UPDATE universities SET latitude = 37.8719, longitude = -122.2585 WHERE name = 'University of California, Berkeley';
UPDATE universities SET latitude = 34.0689, longitude = -118.4452 WHERE name = 'University of California, Los Angeles';
UPDATE universities SET latitude = 40.0067, longitude = -83.0305 WHERE name = 'The Ohio State University';
UPDATE universities SET latitude = 40.7982, longitude = -77.8599 WHERE name = 'Pennsylvania State University';
UPDATE universities SET latitude = 30.2849, longitude = -97.7341 WHERE name = 'University of Texas at Austin';
UPDATE universities SET latitude = 33.7756, longitude = -84.3963 WHERE name = 'Georgia Institute of Technology';
UPDATE universities SET latitude = 47.6553, longitude = -122.3035 WHERE name = 'University of Washington';
UPDATE universities SET latitude = 40.4237, longitude = -86.9212 WHERE name = 'Purdue University';
UPDATE universities SET latitude = 40.1020, longitude = -88.2272 WHERE name = 'University of Illinois Urbana-Champaign';
UPDATE universities SET latitude = 33.4242, longitude = -111.9281 WHERE name = 'Arizona State University';
UPDATE universities SET latitude = 40.7295, longitude = -73.9965 WHERE name = 'New York University';
UPDATE universities SET latitude = 40.4433, longitude = -79.9436 WHERE name = 'Carnegie Mellon University';
UPDATE universities SET latitude = 43.6629, longitude = -79.3957 WHERE name = 'University of Toronto';
UPDATE universities SET latitude = 49.2606, longitude = -123.2460 WHERE name = 'University of British Columbia';
UPDATE universities SET latitude = 51.7548, longitude = -1.2544 WHERE name = 'University of Oxford';
UPDATE universities SET latitude = 51.4988, longitude = -0.1749 WHERE name = 'Imperial College London';
UPDATE universities SET latitude = -37.7963, longitude = 144.9614 WHERE name = 'University of Melbourne';
UPDATE universities SET latitude = -33.8886, longitude = 151.1873 WHERE name = 'University of Sydney';
UPDATE universities SET latitude = 48.1497, longitude = 11.5679 WHERE name = 'Technical University of Munich';
UPDATE universities SET latitude = 47.3763, longitude = 8.5477 WHERE name = 'ETH Zurich';
UPDATE universities SET latitude = 1.2966, longitude = 103.7764 WHERE name = 'National University of Singapore';
UPDATE universities SET latitude = 35.7126, longitude = 139.7620 WHERE name = 'University of Tokyo';
UPDATE universities SET latitude = 37.4599, longitude = 126.9519 WHERE name = 'Seoul National University';
UPDATE universities SET latitude = 40.0000, longitude = 116.3264 WHERE name = 'Tsinghua University';
UPDATE universities SET latitude = 19.1334, longitude = 72.9133 WHERE name = 'Indian Institute of Technology Bombay';
UPDATE universities SET latitude = 23.7340, longitude = 90.3928 WHERE name = 'University of Dhaka';
UPDATE universities SET latitude = 23.7265, longitude = 90.3925 WHERE name = 'Bangladesh University of Engineering and Technology';
UPDATE universities SET latitude = 23.8151, longitude = 90.4255 WHERE name = 'North South University';
UPDATE universities SET latitude = 23.7727, longitude = 90.4255 WHERE name = 'BRAC University';
UPDATE universities SET latitude = -33.9577, longitude = 18.4612 WHERE name = 'University of Cape Town';

-- A few starter places at Eastern Michigan University so the new tabs aren't empty.
-- Students can suggest more from the website.
INSERT INTO places (university_id, type, name, description)
SELECT id, 'DORM', 'Walton Hall', 'Residence hall on the west side of campus'
FROM universities WHERE name = 'Eastern Michigan University';

INSERT INTO places (university_id, type, name, description)
SELECT id, 'DORM', 'Putnam Hall', 'Residence hall connected to Walton Hall'
FROM universities WHERE name = 'Eastern Michigan University';

INSERT INTO places (university_id, type, name, description)
SELECT id, 'DORM', 'Hill Hall', 'High-rise residence hall'
FROM universities WHERE name = 'Eastern Michigan University';

INSERT INTO places (university_id, type, name, description)
SELECT id, 'DINING', 'The Commons', 'Main all-you-care-to-eat dining hall'
FROM universities WHERE name = 'Eastern Michigan University';

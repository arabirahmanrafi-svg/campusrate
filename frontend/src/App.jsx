import { Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar.jsx';
import HomePage from './pages/HomePage.jsx';
import UniversityPage from './pages/UniversityPage.jsx';
import ReviewFormPage from './pages/ReviewFormPage.jsx';
import PlacePage from './pages/PlacePage.jsx';
import QuestionPage from './pages/QuestionPage.jsx';
import RankingsPage from './pages/RankingsPage.jsx';
import ComparePage from './pages/ComparePage.jsx';
import FitPage from './pages/FitPage.jsx';
import WorthItPage from './pages/WorthItPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import MyReviewsPage from './pages/MyReviewsPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import NotificationsPage from './pages/NotificationsPage.jsx';
import AdminPage from './pages/AdminPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';

export default function App() {
  return (
    <>
      <Navbar />
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/universities/:id" element={<UniversityPage />} />
          <Route path="/universities/:id/review" element={<ReviewFormPage />} />
          <Route path="/reviews/:reviewId/edit" element={<ReviewFormPage />} />
          <Route path="/places/:id" element={<PlacePage />} />
          <Route path="/questions/:id" element={<QuestionPage />} />
          <Route path="/rankings" element={<RankingsPage />} />
          <Route path="/compare" element={<ComparePage />} />
          <Route path="/fit" element={<FitPage />} />
          <Route path="/worth-it" element={<WorthItPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/me/reviews" element={<MyReviewsPage />} />
          <Route path="/me/profile" element={<ProfilePage />} />
          <Route path="/me/notifications" element={<NotificationsPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <footer className="footer">
        <div className="container">CampusRate · Reviews are written by students and reflect their own experience.</div>
      </footer>
    </>
  );
}

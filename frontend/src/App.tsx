import { Navigate, Route, Routes } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import ProduitsPage from './pages/ProduitsPage';
import ProduitDetailPage from './pages/ProduitDetailPage';
import CategoriesPage from './pages/CategoriesPage';
import ClientsPage from './pages/ClientsPage';
import ClientDetailPage from './pages/ClientDetailPage';
import DevisPage from './pages/DevisPage';
import DevisClientPage from './pages/DevisClientPage';
import CommandesPage from './pages/CommandesPage';
import FacturesPage from './pages/FacturesPage';
import StockPage from './pages/StockPage';
import MarquesPage from './pages/MarquesPage';
import FournisseursPage from './pages/FournisseursPage';
import FournisseurDetailPage from './pages/FournisseurDetailPage';
import CommandesFournisseurPage from './pages/CommandesFournisseurPage';
import ParametresPage from './pages/ParametresPage';
import TableauBordPage from './pages/TableauBordPage';
import UtilisateursPage from './pages/UtilisateursPage';
import InvitationPage from './pages/InvitationPage';
import ProtectedRoute from './auth/ProtectedRoute';
import Layout from './components/Layout';
import type { ReactNode } from 'react';

function page(node: ReactNode) {
  return <Layout>{node}</Layout>;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Espace client : ouvert depuis le lien recu par email, sans compte */}
      <Route path="/devis-client/:token" element={<DevisClientPage />} />

      {/* Reponse a une invitation : le compte n'est pas encore utilisable */}
      <Route path="/invitation/:token" element={<InvitationPage />} />

      <Route element={<ProtectedRoute />}>
        {/* Point d'entree : le tableau de bord se faconne selon le role */}
        <Route path="/tableau-de-bord" element={page(<TableauBordPage />)} />
        <Route path="/produits" element={page(<ProduitsPage />)} />
        <Route path="/produits/:id" element={page(<ProduitDetailPage />)} />
        <Route path="/categories" element={page(<CategoriesPage />)} />
        <Route path="/clients" element={page(<ClientsPage />)} />
        <Route path="/clients/:id" element={page(<ClientDetailPage />)} />
        <Route path="/devis" element={page(<DevisPage />)} />
        <Route path="/commandes" element={page(<CommandesPage />)} />
        <Route path="/factures" element={page(<FacturesPage />)} />
        <Route path="/stock" element={page(<StockPage />)} />
        <Route path="/marques" element={page(<MarquesPage />)} />
        <Route path="/fournisseurs" element={page(<FournisseursPage />)} />
        <Route path="/fournisseurs/:id" element={page(<FournisseurDetailPage />)} />
        <Route path="/commandes-fournisseur" element={page(<CommandesFournisseurPage />)} />
        <Route path="/utilisateurs" element={page(<UtilisateursPage />)} />
        <Route path="/parametres" element={page(<ParametresPage />)} />
      </Route>

      <Route path="/" element={<Navigate to="/tableau-de-bord" replace />} />
      <Route path="*" element={<Navigate to="/tableau-de-bord" replace />} />
    </Routes>
  );
}

export default App;

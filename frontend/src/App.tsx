import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes, Navigate } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import { DashboardLayout } from "@/components/DashboardLayout";
import { OwnerLayout } from "@/components/OwnerLayout";
import Login from "./pages/Login";
import NotFound from "./pages/NotFound";

// Admin pages
import SuperAdminDashboard from "./pages/admin/SuperAdminDashboard";
import TenantsList from "./pages/admin/TenantsList";
import TenantCreate from "./pages/admin/TenantCreate";
import TenantDetail from "./pages/admin/TenantDetail";

// Tenant pages
import TenantDashboard from "./pages/tenant/TenantDashboard";
import BookingsList from "./pages/tenant/BookingsList";
import BookingDetail from "./pages/tenant/BookingDetail";
import OwnersList from "./pages/tenant/OwnersList";
import OwnerCreate from "./pages/tenant/OwnerCreate";
import OwnerDetail from "./pages/tenant/OwnerDetail";
import PropertyContracts from "./pages/tenant/PropertyContracts";
import PropertiesList from "./pages/tenant/PropertiesList";
import PropertyDetail from "./pages/tenant/PropertyDetail";
import PropertyCreate from "./pages/tenant/PropertyCreate";
import PropertyEdit from "./pages/tenant/PropertyEdit";
import DocumentsList from "./pages/tenant/DocumentsList";
import DocumentDetail from "./pages/tenant/DocumentDetail";
import F24List from "./pages/tenant/F24List";
import SettlementsList from "./pages/tenant/SettlementsList";
import CUList from "./pages/tenant/CUList";
import AuditLog from "./pages/tenant/AuditLog";
import ImportBookings from "./pages/tenant/ImportBookings";
import Reconciliation from "./pages/tenant/Reconciliation";
import TenantSettings from "./pages/tenant/TenantSettings";
import UsersList from "./pages/tenant/UsersList";
import OTARegistry from "./pages/tenant/OTARegistry";
import TouristTaxSettings from "./pages/tenant/TouristTaxSettings";

// Owner pages
import OwnerDashboard from "./pages/owner/OwnerDashboard";
import OwnerBookings from "./pages/owner/OwnerBookings";
import OwnerDocuments from "./pages/owner/OwnerDocuments";
import OwnerSettlements from "./pages/owner/OwnerSettlements";
import OwnerCU from "./pages/owner/OwnerCU";
import GuestDocuments from "./pages/GuestDocuments";
const queryClient = new QueryClient();

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function AppRoutes() {
  const { isAuthenticated, user } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to={user?.role === 'super_admin' ? '/admin' : user?.role === 'owner_user' ? '/owner' : '/dashboard'} replace /> : <Login />} />

      {/* Super Admin */}
      <Route path="/admin" element={<ProtectedRoute><DashboardLayout /></ProtectedRoute>}>
        <Route index element={<SuperAdminDashboard />} />
        <Route path="tenants" element={<TenantsList />} />
        <Route path="tenants/new" element={<TenantCreate />} />
        <Route path="tenants/:id" element={<TenantDetail />} />
        <Route path="audit" element={<AuditLog />} />
      </Route>

      {/* Tenant Admin / PM */}
      <Route path="/" element={<ProtectedRoute><DashboardLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<TenantDashboard />} />
        <Route path="bookings" element={<BookingsList />} />
        <Route path="bookings/:id" element={<BookingDetail />} />
        <Route path="owners" element={<OwnersList />} />
        <Route path="owners/new" element={<OwnerCreate />} />
        <Route path="owners/:id" element={<OwnerDetail />} />
        <Route path="properties" element={<PropertiesList />} />
        <Route path="properties/new" element={<PropertyCreate />} />
        <Route path="properties/:id" element={<PropertyDetail />} />
        <Route path="properties/:id/edit" element={<PropertyEdit />} />
        <Route path="properties/:id/contracts" element={<PropertyContracts />} />
        <Route path="documents" element={<DocumentsList />} />
        <Route path="documents/:id" element={<DocumentDetail />} />
        <Route path="f24" element={<F24List />} />
        <Route path="settlements" element={<SettlementsList />} />
        <Route path="cu" element={<CUList />} />
        <Route path="audit" element={<AuditLog />} />
        <Route path="import/bookings" element={<ImportBookings />} />
        <Route path="reconciliation" element={<Reconciliation />} />
        <Route path="settings" element={<TenantSettings />} />
        <Route path="users" element={<UsersList />} />
        <Route path="ota" element={<OTARegistry />} />
        <Route path="tourist-tax" element={<TouristTaxSettings />} />
      </Route>

      {/* Owner */}
      <Route path="/owner" element={<ProtectedRoute><OwnerLayout /></ProtectedRoute>}>
        <Route index element={<OwnerDashboard />} />
        <Route path="bookings" element={<OwnerBookings />} />
        <Route path="documents" element={<OwnerDocuments />} />
        <Route path="settlements" element={<OwnerSettlements />} />
        <Route path="cu" element={<OwnerCU />} />
      </Route>

      <Route path="/guest/documents" element={<GuestDocuments />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <AuthProvider>
        <BrowserRouter basename={import.meta.env.BASE_URL}>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;

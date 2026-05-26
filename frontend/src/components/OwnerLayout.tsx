import { Outlet } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { NavLink } from '@/components/NavLink';
import { LayoutDashboard, CalendarDays, FileText, Receipt, Award, LogOut, Cloud } from 'lucide-react';

const ownerNav = [
  { title: 'Dashboard', url: '/owner', icon: LayoutDashboard },
  { title: 'Prenotazioni', url: '/owner/bookings', icon: CalendarDays },
  { title: 'Documenti', url: '/owner/documents', icon: FileText },
  { title: 'Liquidazioni', url: '/owner/settlements', icon: Receipt },
  { title: 'CU', url: '/owner/cu', icon: Award },
];

export function OwnerLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Top bar */}
      <header className="h-14 flex items-center justify-between px-4 border-b bg-card shrink-0">
        <div className="flex items-center gap-2">
          <Cloud className="h-5 w-5 text-primary" />
          <span className="text-sm font-bold text-foreground">Sostituto in Cloud</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-muted-foreground hidden sm:inline">{user?.first_name} {user?.last_name}</span>
          <button onClick={logout} className="text-muted-foreground hover:text-destructive">
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 overflow-auto p-4 pb-20">
        <Outlet />
      </main>

      {/* Bottom nav */}
      <nav className="fixed bottom-0 left-0 right-0 h-16 bg-card border-t flex items-center justify-around z-50">
        {ownerNav.map(item => (
          <NavLink
            key={item.url}
            to={item.url}
            end={item.url === '/owner'}
            className="flex flex-col items-center gap-0.5 text-muted-foreground px-3 py-1"
            activeClassName="text-primary"
          >
            <item.icon className="h-5 w-5" />
            <span className="text-[10px] font-medium">{item.title}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}

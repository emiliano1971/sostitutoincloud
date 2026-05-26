import { useAuth } from '@/contexts/AuthContext';
import {
  Sidebar, SidebarContent, SidebarGroup, SidebarGroupContent,
  SidebarGroupLabel, SidebarMenu, SidebarMenuButton, SidebarMenuItem,
  SidebarHeader, SidebarFooter, useSidebar,
} from '@/components/ui/sidebar';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { NavLink } from '@/components/NavLink';
import { RoleSwitcher } from '@/components/RoleSwitcher';
import {
  LayoutDashboard, Building2, Users, Home, FileText, CalendarDays,
  Upload, GitMerge, Receipt, CreditCard, Award, ScrollText,
  Settings, LogOut, ChevronDown, BookOpen, Globe, MapPin,
} from 'lucide-react';
import appLogo from '@/assets/logo-icon.png';

const superAdminNav = [
  { title: 'Dashboard', url: '/admin', icon: LayoutDashboard },
  { title: 'Tenant', url: '/admin/tenants', icon: Building2 },
  { title: 'Audit Log', url: '/admin/audit', icon: ScrollText },
];

const tenantMainNav = [
  { title: 'Dashboard', url: '/dashboard', icon: LayoutDashboard },
  { title: 'Prenotazioni', url: '/bookings', icon: CalendarDays },
  { title: 'Riconciliazione', url: '/reconciliation', icon: GitMerge },
  { title: 'Proprietari', url: '/owners', icon: Users },
  { title: 'Immobili', url: '/properties', icon: Home },
  { title: 'Utenti', url: '/users', icon: Users },
  { title: 'Anagrafica OTA', url: '/ota', icon: Globe },
  { title: 'Tassa di Soggiorno', url: '/tourist-tax', icon: MapPin },
  { title: 'Configurazione', url: '/settings', icon: Settings },
  { title: 'Audit Log', url: '/audit', icon: ScrollText },
];

const tenantAccountingNav = [
  { title: 'Documenti Fiscali', url: '/documents', icon: FileText },
  { title: 'F24', url: '/f24', icon: CreditCard },
  { title: 'Liquidazioni', url: '/settlements', icon: Receipt },
  { title: 'CU', url: '/cu', icon: Award },
];

const pmMainNav = [
  { title: 'Dashboard', url: '/dashboard', icon: LayoutDashboard },
  { title: 'Prenotazioni', url: '/bookings', icon: CalendarDays },
  { title: 'Riconciliazione', url: '/reconciliation', icon: GitMerge },
];

const pmAccountingNav = [
  { title: 'Documenti Fiscali', url: '/documents', icon: FileText },
  { title: 'Liquidazioni', url: '/settlements', icon: Receipt },
];

export function AppSidebar() {
  const { user, logout } = useAuth();
  const { state } = useSidebar();
  const collapsed = state === 'collapsed';

  if (!user) return null;

  const mainNav = user.role === 'super_admin' ? superAdminNav
    : user.role === 'tenant_admin' ? tenantMainNav
    : pmMainNav;

  const accountingNav = user.role === 'super_admin' ? null
    : user.role === 'tenant_admin' ? tenantAccountingNav
    : pmAccountingNav;

  const renderNavItems = (items: typeof superAdminNav) => (
    <SidebarMenu>
      {items.map(item => (
        <SidebarMenuItem key={item.url}>
          <SidebarMenuButton asChild>
            <NavLink
              to={item.url}
              end={item.url === '/dashboard' || item.url === '/admin'}
              className="text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-foreground"
              activeClassName="bg-sidebar-accent text-sidebar-primary font-medium"
            >
              <item.icon className="h-4 w-4 shrink-0" />
              {!collapsed && <span>{item.title}</span>}
            </NavLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      ))}
    </SidebarMenu>
  );

  return (
    <Sidebar collapsible="icon" className="border-r-0">
      <SidebarHeader className="p-4 border-b border-sidebar-border">
        <div className="flex items-center gap-2">
          <img src={appLogo} alt="Sostituto in Cloud" className="h-8 w-8 shrink-0 object-contain" />
          {!collapsed && (
            <div className="flex flex-col">
              <span className="text-sm font-bold text-sidebar-foreground">Sostituto</span>
              <span className="text-[10px] text-sidebar-muted font-medium tracking-wider uppercase">In Cloud</span>
            </div>
          )}
        </div>
        {!collapsed && user.tenant_name && (
          <div className="mt-3 px-2 py-1.5 rounded-md bg-sidebar-accent">
            <span className="text-[11px] text-sidebar-muted">Tenant</span>
            <p className="text-xs font-medium text-sidebar-foreground truncate">{user.tenant_name}</p>
          </div>
        )}
      </SidebarHeader>

      <SidebarContent className="[scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
        <SidebarGroup>
          <SidebarGroupLabel className="text-sidebar-muted text-[10px] uppercase tracking-wider">
            {user.role === 'super_admin' ? 'Piattaforma' : 'Gestione'}
          </SidebarGroupLabel>
          <SidebarGroupContent>
            {renderNavItems(mainNav)}

            {accountingNav && (
              <Collapsible defaultOpen className="group/collapsible mt-3">
                <CollapsibleTrigger className="flex w-full items-center justify-between px-2 py-1 text-sidebar-muted text-[10px] uppercase tracking-wider hover:text-sidebar-foreground transition-colors">
                  <span className="flex items-center gap-1.5">
                    <BookOpen className="h-3 w-3" />
                    Contabilità
                  </span>
                  {!collapsed && <ChevronDown className="h-3 w-3 transition-transform group-data-[state=open]/collapsible:rotate-180" />}
                </CollapsibleTrigger>
                <CollapsibleContent>
                  {renderNavItems(accountingNav)}
                </CollapsibleContent>
              </Collapsible>
            )}
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className="p-3 border-t border-sidebar-border space-y-2">
        {!collapsed && <RoleSwitcher />}
        {!collapsed && (
          <div className="flex items-center gap-2 px-2">
            <div className="h-7 w-7 rounded-full bg-sidebar-primary/20 flex items-center justify-center">
              <span className="text-[11px] font-semibold text-sidebar-primary">
                {user.first_name[0]}{user.last_name[0]}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-sidebar-foreground truncate">{user.first_name} {user.last_name}</p>
              <p className="text-[10px] text-sidebar-muted truncate">{user.email}</p>
            </div>
          </div>
        )}
        <SidebarMenuButton onClick={logout} className="text-sidebar-foreground/50 hover:text-destructive hover:bg-sidebar-accent">
          <LogOut className="h-4 w-4" />
          {!collapsed && <span className="text-xs">Esci</span>}
        </SidebarMenuButton>
      </SidebarFooter>
    </Sidebar>
  );
}

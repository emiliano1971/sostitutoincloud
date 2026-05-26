import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Plus, Trash2 } from 'lucide-react';

const mockUsersList = [
  { id: 'u2', name: 'Laura Bianchi', email: 'admin@casavacanze.it', role: 'TENANT_ADMIN', status: 'active', scope: 'full' },
  { id: 'u3', name: 'Giovanni Verdi', email: 'pm@casavacanze.it', role: 'PM_USER', status: 'active', scope: 'full' },
  { id: 'u5', name: 'Sara Neri', email: 'sara.neri@casavacanze.it', role: 'PM_USER', status: 'active', scope: 'scoped - 2 immobili' },
  { id: 'u4', name: 'Anna Moretti', email: 'proprietario@email.it', role: 'OWNER_USER', status: 'active', scope: '2 immobili' },
];

const UsersList = () => (
  <div className="space-y-6">
    <div className="flex items-center justify-between">
      <div>
        <h1 className="text-2xl font-bold">Utenti e Scope</h1>
        <p className="text-sm text-muted-foreground">Gestisci gli accessi al tenant</p>
      </div>
      <Button size="sm" className="gap-2"><Plus className="h-4 w-4" /> Invita Utente</Button>
    </div>
    <Card>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nome</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Ruolo</TableHead>
              <TableHead>Scope</TableHead>
              <TableHead>Stato</TableHead>
              <TableHead className="w-10"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {mockUsersList.map(u => (
              <TableRow key={u.id}>
                <TableCell className="font-medium">{u.name}</TableCell>
                <TableCell className="text-sm text-muted-foreground">{u.email}</TableCell>
                <TableCell><Badge variant="outline" className="text-xs">{u.role}</Badge></TableCell>
                <TableCell className="text-sm">{u.scope}</TableCell>
                <TableCell><Badge variant="default" className="text-xs">{u.status}</Badge></TableCell>
                <TableCell><Button variant="ghost" size="icon" className="h-7 w-7 text-destructive"><Trash2 className="h-3.5 w-3.5" /></Button></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
);

export default UsersList;

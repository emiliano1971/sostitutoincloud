import * as React from 'react';
import { Input } from '@/components/ui/input';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Campo password con toggle mostra/nascondi. Drop-in per <Input>: accetta le stesse
 * props tranne `type`, che è gestito internamente dallo stato di visibilità.
 */
type PasswordInputProps = Omit<React.ComponentProps<typeof Input>, 'type'>;

const PasswordInput = React.forwardRef<HTMLInputElement, PasswordInputProps>(
  ({ className, disabled, ...props }, ref) => {
    const [visible, setVisible] = React.useState(false);

    return (
      <div className="relative">
        <Input
          ref={ref}
          type={visible ? 'text' : 'password'}
          // pr-10: senza, una password lunga scorrerebbe sotto l'icona proprio quando
          // la si rende visibile.
          className={cn('pr-10', className)}
          disabled={disabled}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible(v => !v)}
          // Impedisce che il click sposti il focus fuori dal campo: dove l'input ha un
          // onBlur (es. il banner requisiti del cambio password) si chiuderebbe di scatto.
          onMouseDown={e => e.preventDefault()}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
          // Fuori dal giro del Tab: la navigazione da tastiera va dal campo al submit.
          tabIndex={-1}
          disabled={disabled}
          aria-label={visible ? 'Nascondi password' : 'Mostra password'}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
    );
  },
);
PasswordInput.displayName = 'PasswordInput';

export default PasswordInput;

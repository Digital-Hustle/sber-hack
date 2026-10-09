import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { completeSignIn } from "@/entities/session";
import { Button, EmptyState, Spinner } from "@/shared/ui";
import { routes } from "@/shared/const/router";

/** Возврат из Keycloak (PKCE): код → токены в памяти → в нужный раздел. */
export const CallbackPage = () => {
  const nav = useNavigate();
  const [error, setError] = useState(false);
  const once = useRef(false);
  useEffect(() => {
    if (once.current) return;
    once.current = true;
    completeSignIn().then((to) => nav(to, { replace: true })).catch(() => setError(true));
  }, [nav]);
  if (error) return <EmptyState title="Вход не завершён" text="Ссылка входа устарела или была открыта повторно" className="min-h-svh" action={<Link to={routes.operator}><Button>Войти снова</Button></Link>} />;
  return <div className="grid min-h-svh place-items-center"><Spinner className="size-8" /></div>;
};

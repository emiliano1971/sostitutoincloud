import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import "./index.css";
import { loadConfig } from "@/config/AppConfig";
import { LookupProvider } from "@/contexts/LookupContext";

loadConfig().then(() => {
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <LookupProvider>
        <App />
      </LookupProvider>
    </React.StrictMode>
  );
}).catch(err => {
  console.error('Errore caricamento configurazione:', err);
  document.body.innerHTML = '<h1>Errore configurazione applicazione</h1>';
});

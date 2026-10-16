import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";

const root = createRoot(document.getElementById("root")!);
const data = window.__TESTHARNESS__;
root.render(
  <StrictMode>
    {data ? <App d={data} /> : (
      <div className="shell">
        <h1>TestHarness quality</h1>
        <div className="empty">
          No data.js next to this page. Generate the dashboard with <code>./th dashboard</code> (Windows:{" "}
          <code>th.cmd dashboard</code>); it writes index.html and data.js to target/dashboard.
        </div>
      </div>
    )}
  </StrictMode>,
);

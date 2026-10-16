import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

/**
 * Inlines the built script and stylesheet into index.html, so the dashboard is one file that
 * opens from disk (file://) as well as over HTTP. Data stays outside, in data.js, which the
 * CLI writes next to the page: a classic script, so it also loads from file://.
 */
function inlineAssets(): Plugin {
  return {
    name: "testharness-inline-assets",
    enforce: "post",
    generateBundle(_options, bundle) {
      const html = bundle["index.html"];
      if (!html || html.type !== "asset") return;
      let source = String(html.source);
      for (const [name, chunk] of Object.entries(bundle)) {
        if (chunk.type === "chunk" && chunk.isEntry) {
          const tag = new RegExp(`<script[^>]*src="[^"]*${name}"[^>]*></script>`);
          const code = chunk.code.replace(/<\/script/gi, "<\\/script");
          source = source.replace(tag, () => `<script type="module">${code}</script>`);
          delete bundle[name];
        } else if (chunk.type === "asset" && name.endsWith(".css")) {
          const tag = new RegExp(`<link[^>]*href="[^"]*${name}"[^>]*>`);
          source = source.replace(tag, () => `<style>${String(chunk.source)}</style>`);
          delete bundle[name];
        }
      }
      html.source = source;
    },
  };
}

export default defineConfig({
  base: "./",
  plugins: [react(), inlineAssets()],
  build: {
    outDir: "../harness/harness-insights/src/main/resources/io/github/raktimchandra/testharness/insights/dashboard/site",
    emptyOutDir: true,
    assetsInlineLimit: 100_000_000,
    cssCodeSplit: false,
    modulePreload: false,
    reportCompressedSize: false,
  },
});

import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
const root = `${process.env.SITE_OUTPUT || 'site-output'}/observability/latest`;
await mkdir(root, { recursive: true });
const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || undefined, args: ['--no-sandbox'] });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
  await page.goto('http://localhost:3000/d/payguard-payment-reliability?orgId=1&from=now-30m&to=now', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.getByText('PayGuard - Payment Service SRE').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(12000);
  const body = await page.locator('body').innerText();
  if (body.includes('Data source not found') || body.includes('Failed to fetch')) throw Error('Grafana panels could not query Prometheus');
  await page.screenshot({ path: `${root}/grafana.png`, fullPage: true });
  console.log('Captured actual Grafana dashboard');
} finally { await browser.close(); }

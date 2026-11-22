// Records a demo video + screenshots of a running Explainly app.
//
//   APP_URL=http://localhost:8088 VIDEO_URL="https://www.youtube.com/watch?v=..." \
//   TRANSCRIPT_FILE=./transcript.txt node capture.mjs
//
// Phase 1 (not recorded) analyzes the video so the recording never shows waiting time.
// Phase 2 records a clean walkthrough. Output goes to ./output (MP4 if ffmpeg is installed).
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { chromium } from 'playwright'

const APP_URL = (process.env.APP_URL ?? 'http://localhost:8088').replace(/\/$/, '')
const VIDEO_URL = process.env.VIDEO_URL ?? 'https://www.youtube.com/watch?v=dQw4w9WgXcQ'
const TRANSCRIPT_FILE = process.env.TRANSCRIPT_FILE
const CHAT_QUESTION = process.env.CHAT_QUESTION ?? 'Explain the main idea with a real-world example'
const OUT = resolve(process.env.OUT_DIR ?? './output')
const W = 1280
const H = 720
const pause = (page, ms = 1200) => page.waitForTimeout(ms)

rmSync(OUT, { recursive: true, force: true })
mkdirSync(join(OUT, 'screenshots'), { recursive: true })
/** Screenshot of one element with the sticky header hidden so it never overlaps. */
async function elementShot(page, locator, name) {
  await page.addStyleTag({ content: 'header.sticky{visibility:hidden!important}', }).then((h) => h.evaluate((el) => el.setAttribute('data-capture', '1')))
  await locator.screenshot({ path: join(OUT, 'screenshots', `${name}.png`) })
  await page.evaluate(() => document.querySelectorAll('style[data-capture]').forEach((el) => el.remove()))
}
const shot = (page, name, opts = {}) => page.screenshot({ path: join(OUT, 'screenshots', `${name}.png`), ...opts })

const browser = await chromium.launch()

// ---------- Phase 1: prepare the analysis (not recorded) ----------
console.log('1/3 Preparing analysis (this can take a few minutes with a real AI provider)…')
const prep = await browser.newContext({ viewport: { width: W, height: H } })
const p = await prep.newPage()
await p.goto(APP_URL)
await p.fill('input[type=url]', VIDEO_URL)
await p.click('button:has-text("Analyze video")')
await p.waitForURL(/\/learn\/\d+/, { timeout: 60_000 })
const learnUrl = p.url()
const ready = p.waitForSelector('text=Timeline', { timeout: 15 * 60_000 })
const needsTranscript = p.waitForSelector('text=We need the transcript', { timeout: 15 * 60_000 }).then(() => 'transcript')
const first = await Promise.race([ready.then(() => 'ready'), needsTranscript])
if (first === 'transcript') {
  if (!TRANSCRIPT_FILE) {
    console.error('\nThis video needs a transcript. Save it to a text file and run again with TRANSCRIPT_FILE=./transcript.txt')
    await browser.close()
    process.exit(1)
  }
  await p.fill('#transcript-text', readFileSync(TRANSCRIPT_FILE, 'utf8'))
  await p.click('button:has-text("Explain this transcript")')
  await p.waitForSelector('text=Timeline', { timeout: 15 * 60_000 })
}
// Pre-generate quiz and flashcards so the recording doesn't wait on the AI.
await p.click('role=tab[name="Quiz"]')
if (await p.locator('button:has-text("Generate quiz")').count()) {
  await p.click('button:has-text("Generate quiz")')
  await p.waitForSelector('text=Question 1', { timeout: 5 * 60_000 })
}
await p.click('role=tab[name="Flashcards"]')
if (await p.locator('button:has-text("Create flashcards")').count()) {
  await p.click('button:has-text("Create flashcards")')
  await p.waitForSelector('text=/Card 1 of/', { timeout: 5 * 60_000 })
}
const storage = await prep.storageState()
await prep.close()

// ---------- Phase 2: recorded walkthrough ----------
console.log('2/3 Recording walkthrough…')
const rec = await browser.newContext({
  viewport: { width: W, height: H },
  deviceScaleFactor: 1,
  storageState: storage,
  recordVideo: { dir: join(OUT, 'raw'), size: { width: W, height: H } },
})
const page = await rec.newPage()
await page.goto(APP_URL)
await pause(page, 1800)
await page.click('input[type=url]')
await page.keyboard.type(VIDEO_URL, { delay: 25 })
await pause(page, 600)
await page.click('button:has-text("Analyze video")')
await page.waitForSelector('text=Timeline', { timeout: 60_000 })
await pause(page, 2500)
await shot(page, '02-dashboard-overview')
await page.mouse.wheel(0, 500)
await pause(page, 1800)
await page.mouse.wheel(0, -500)

await page.click('role=tab[name="Topics"]')
await pause(page, 1500)
await page.locator('article').first().scrollIntoViewIfNeeded()
await page.mouse.wheel(0, 450)
await pause(page, 2200)
await shot(page, '03-topic-explanation')
const diagram = page.locator('figure').first()
if (await diagram.count()) {
  await diagram.scrollIntoViewIfNeeded()
  await pause(page, 1800)
  await elementShot(page, diagram, '04-diagram')
}
await page.mouse.wheel(0, -2000)
await page.click('role=tab[name="Beginner"]')
await pause(page, 2200)
await page.click('role=tab[name="Concepts"]')
await pause(page, 2200)
await shot(page, '05-concepts')

const tools = page.locator('section[aria-label="Learning tools"]')
await tools.scrollIntoViewIfNeeded()
await page.click('role=tab[name="AI Chat"]')
await pause(page, 800)
await page.click('input[placeholder="Ask a question about this video…"]')
await page.keyboard.type(CHAT_QUESTION, { delay: 30 })
await page.keyboard.press('Enter')
await page.waitForSelector('text=Sources:', { timeout: 5 * 60_000 }).catch(() => undefined)
await pause(page, 2500)
await elementShot(page, tools, '06-ai-chat')

await page.click('role=tab[name="Quiz"]')
await pause(page, 1200)
const questions = tools.locator('fieldset')
for (let i = 0; i < Math.min(await questions.count(), 3); i++) {
  await questions.nth(i).scrollIntoViewIfNeeded()
  await questions.nth(i).locator('label').first().click()
  await pause(page, 700)
}
for (let i = 3; i < (await questions.count()); i++) await questions.nth(i).locator('label').first().click()
await page.click('button:has-text("Check answers")')
await pause(page, 1500)
await tools.scrollIntoViewIfNeeded()
await pause(page, 2000)
await elementShot(page, tools, '07-quiz-results')

await page.click('role=tab[name="Flashcards"]')
await tools.scrollIntoViewIfNeeded()
await pause(page, 1500)
await page.click('button[aria-label="Show answer"]')
await pause(page, 2000)
await elementShot(page, tools, '08-flashcards')

await page.click('role=tab[name="Notes"]')
await pause(page, 2500)
await page.goto(`${APP_URL}/library`)
await pause(page, 2500)
await rec.close() // finalizes the video file

// ---------- Phase 3: extra screenshots (not recorded) ----------
console.log('3/3 Taking extra screenshots…')
const hi = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, storageState: storage })
const h = await hi.newPage()
await h.goto(APP_URL)
await pause(h, 1500)
await shot(h, '01-home')
await h.goto(learnUrl)
await h.waitForSelector('text=Timeline')
await pause(h, 1500)
await shot(h, '02-dashboard-hires')
await h.emulateMedia({ colorScheme: 'dark' })
await h.reload()
await h.waitForSelector('text=Timeline')
await pause(h, 1500)
await shot(h, '09-dashboard-dark')
await hi.close()

const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true, storageState: storage })
const m = await phone.newPage()
await m.goto(learnUrl)
await m.waitForSelector('text=Timeline')
await pause(m, 1500)
await shot(m, '10-mobile-learning')
await m.goto(APP_URL)
await pause(m, 1200)
await shot(m, '11-mobile-home')
await phone.close()
await browser.close()

// Convert the recording to MP4 (best for LinkedIn) when ffmpeg is available.
const raw = readdirSync(join(OUT, 'raw')).find((f) => f.endsWith('.webm'))
const webm = join(OUT, 'explainly-demo.webm')
renameSync(join(OUT, 'raw', raw), webm)
rmSync(join(OUT, 'raw'), { recursive: true, force: true })
try {
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', webm, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-movflags', '+faststart', join(OUT, 'explainly-demo.mp4')])
  rmSync(webm)
  console.log(`\nDone! Video: ${join(OUT, 'explainly-demo.mp4')}`)
} catch {
  console.log(`\nDone! Video (WebM): ${webm}\nInstall ffmpeg to get an MP4 automatically.`)
}
console.log(`Screenshots: ${join(OUT, 'screenshots')}`)
if (!existsSync(join(OUT, 'screenshots', '04-diagram.png'))) console.log('(No diagram was generated for this video.)')

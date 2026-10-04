import {
  Bell,
  Check,
  Download,
  Flame,
  Footprints,
  ShieldCheck,
  Sparkles,
  Trophy,
} from "lucide-react";
import { Button } from "@/components/ui/button";

const week = [
  1, 2, 3, 4, 0, 2, 3, 4, 4, 1, 0, 3, 4, 4, 2, 1, 3, 4, 4, 4, 0, 2, 3,
  4, 4, 4, 1, 3, 4, 4, 4, 2, 3, 4, 4,
];

function Mark({ small = false }: { small?: boolean }) {
  return (
    <span className={small ? "brand-mark brand-mark--small" : "brand-mark"} aria-hidden="true">
      <i />
      <i />
      <i />
      <i />
    </span>
  );
}

function ProgressGrid({ compact = false }: { compact?: boolean }) {
  return (
    <div className={compact ? "progress-grid progress-grid--compact" : "progress-grid"} aria-label="Five weeks of goal progress">
      {week.map((level, index) => (
        <span key={index} className={`level-${level}`} />
      ))}
    </div>
  );
}

function PhonePreview() {
  return (
    <div className="phone-stage" aria-label="Preview of the WinterArc Android app">
      <div className="orbit-note orbit-note--top">
        <Flame size={17} />
        <span><strong>23 days</strong> in motion</span>
      </div>

      <div className="phone">
        <div className="phone-bar">
          <span>9:41</span>
          <span className="phone-sensors" aria-hidden="true"><i /><i /><i /></span>
        </div>
        <div className="app-head">
          <span className="app-wordmark"><Mark small /> WinterArc</span>
          <span className="avatar">M</span>
        </div>
        <div className="app-body">
          <p className="app-eyebrow">MONDAY · OCTOBER 5</p>
          <h2>Good morning.</h2>
          <p className="app-summary">4 targets left today</p>

          <article className="goal-surface">
            <div className="goal-title-row">
              <span className="goal-icon"><Footprints size={18} /></span>
              <div>
                <h3>Road to 10K</h3>
                <p>Today · 10 km</p>
              </div>
              <span className="complete-button"><Check size={15} /> Log</span>
            </div>
            <ProgressGrid compact />
            <div className="goal-meta"><span><Flame size={14} /> 23 day streak</span><span>93%</span></div>
            <div className="goal-line"><span /></div>
          </article>

          <article className="goal-surface goal-surface--secondary">
            <div className="goal-title-row">
              <span className="goal-icon goal-icon--violet">Aa</span>
              <div>
                <h3>Read 20 pages</h3>
                <p>Weekdays · 20 pages</p>
              </div>
              <span className="complete-button"><Check size={15} /> Log</span>
            </div>
            <ProgressGrid compact />
          </article>
        </div>
      </div>

      <div className="orbit-note orbit-note--bottom">
        <Trophy size={17} />
        <span>Milestone <strong>within reach</strong></span>
      </div>
    </div>
  );
}

export default function Home() {
  return (
    <main>
      <header className="site-nav">
        <a className="site-logo" href="#top" aria-label="WinterArc home">
          <Mark small />
          <span>WinterArc</span>
        </a>
        <nav aria-label="Primary navigation">
          <a href="#how-it-works">How it works</a>
          <a href="#features">Features</a>
          <Button className="nav-download" asChild>
            <a href="/downloads/WinterArc-1.0.0-preview.apk" download>
              <Download /> Download
            </a>
          </Button>
        </nav>
      </header>

      <section id="top" className="hero">
        <div className="hero-copy">
          <div className="availability"><span /> Android preview · Version 1.0.0</div>
          <h1>Turn intention into <em>evidence.</em></h1>
          <p className="hero-lede">
            WinterArc turns the goals you care about into visible daily progress—so every walk,
            page, rep, and focused minute becomes part of a record you can keep building.
          </p>
          <div className="hero-actions">
            <Button className="primary-download" size="lg" asChild>
              <a href="/downloads/WinterArc-1.0.0-preview.apk" download>
                <Download /> Download for Android
              </a>
            </Button>
            <a className="text-link" href="#how-it-works">See how WinterArc works</a>
          </div>
          <p className="download-note">Preview build · Android 8.0 or newer · 11 MB</p>
        </div>
        <PhonePreview />
      </section>

      <section className="principle" aria-label="WinterArc principle">
        <span>SET IT.</span>
        <span>SHOW UP.</span>
        <span className="principle-accent">SEE IT COMPOUND.</span>
      </section>

      <section id="how-it-works" className="modes-section">
        <div className="section-intro">
          <p className="kicker">HOW WINTERARC WORKS</p>
          <h2>Pick the rhythm that fits the goal.</h2>
          <p>Not every goal grows the same way. WinterArc gives you two focused modes without turning self-improvement into spreadsheet work.</p>
        </div>

        <div className="mode-row">
          <div className="mode-number">01</div>
          <div className="mode-copy">
            <span className="mode-label">CONSISTENCY</span>
            <h3>Do the work. Keep the promise.</h3>
            <p>Set a repeatable target—walk 5 km, read 20 pages, meditate for 10 minutes—and build a rhythm around showing up.</p>
          </div>
          <div className="mode-visual consistency-visual" aria-label="A week with five completed goal days">
            {['M','T','W','T','F','S','S'].map((day, index) => (
              <span key={`${day}-${index}`} className={index < 5 || index === 6 ? "done" : "rest"}>
                <small>{day}</small>{index < 5 || index === 6 ? <Check /> : <i />}
              </span>
            ))}
          </div>
        </div>

        <div className="mode-row mode-row--dark">
          <div className="mode-number">02</div>
          <div className="mode-copy">
            <span className="mode-label">PROGRESSION</span>
            <h3>Start where you are. Move when you’re ready.</h3>
            <p>Choose a starting point and milestone. Your target steps up only after a successful day—never because the calendar says so.</p>
          </div>
          <div className="steps-visual" aria-label="Progression from 1 kilometre to 10 kilometres">
            <span style={{ height: '24%' }}><small>1</small></span>
            <span style={{ height: '38%' }}><small>2</small></span>
            <span style={{ height: '52%' }}><small>4</small></span>
            <span style={{ height: '68%' }}><small>6</small></span>
            <span style={{ height: '84%' }}><small>8</small></span>
            <span className="active" style={{ height: '100%' }}><small>10 km</small></span>
          </div>
        </div>
      </section>

      <section id="features" className="proof-section">
        <div className="proof-copy">
          <p className="kicker">PROGRESS YOU CAN READ AT A GLANCE</p>
          <h2>A record that doesn’t lie.</h2>
          <p>
            Every scheduled day becomes a square. Completed, partial, rest, missed, and above-target
            days stay distinct—giving you an honest picture without punishing the days you chose to rest.
          </p>
          <dl className="stat-line">
            <div><dt>Current streak</dt><dd>23 days</dd></div>
            <div><dt>Completion rate</dt><dd>93%</dd></div>
            <div><dt>Total distance</dt><dd>184 km</dd></div>
          </dl>
        </div>
        <div className="year-visual" aria-label="Example annual progress heatmap">
          <div className="month-labels"><span>JAN</span><span>MAR</span><span>MAY</span><span>JUL</span><span>SEP</span><span>NOV</span></div>
          <div className="year-grid">
            {Array.from({ length: 161 }, (_, index) => {
              const level = index > 138 ? 0 : ((index * 7 + index % 11) % 5);
              return <span key={index} className={`level-${level}`} />;
            })}
          </div>
          <div className="year-caption"><span>Last 12 months</span><span><i className="level-1" /> Less <i className="level-4" /> More</span></div>
        </div>
      </section>

      <section className="feature-list" aria-label="WinterArc features">
        <div className="feature-heading">
          <p className="kicker">BUILT FOR REAL LIFE</p>
          <h2>Structure without the pressure.</h2>
        </div>
        <article>
          <span className="feature-icon"><Bell /></span>
          <div><h3>A nudge at the right time</h3><p>Set local reminders around your schedule. They stay aligned when your timezone or daylight-saving time changes.</p></div>
          <span className="feature-index">01</span>
        </article>
        <article>
          <span className="feature-icon"><ShieldCheck /></span>
          <div><h3>Your goals stay on your device</h3><p>WinterArc is offline-first. Your goal history is available without a connection and remains stored locally.</p></div>
          <span className="feature-index">02</span>
        </article>
        <article>
          <span className="feature-icon"><Sparkles /></span>
          <div><h3>Milestones that lead somewhere</h3><p>When you reach a progression milestone, maintain it, extend the journey, or mark the arc complete.</p></div>
          <span className="feature-index">03</span>
        </article>
      </section>

      <section className="download-section">
        <div>
          <p className="kicker">YOUR NEXT ARC STARTS TODAY</p>
          <h2>One goal.<br />One honest day at a time.</h2>
        </div>
        <div className="download-panel">
          <Mark />
          <p>Download the current WinterArc preview directly to your Android device.</p>
          <Button className="footer-download" size="lg" asChild>
            <a href="/downloads/WinterArc-1.0.0-preview.apk" download>
              <Download /> Download WinterArc
            </a>
          </Button>
          <span>Android 8.0+ · Preview release</span>
        </div>
      </section>

      <footer>
        <a className="site-logo" href="#top"><Mark small /><span>WinterArc</span></a>
        <p>Turn personal goals into visible daily progress.</p>
        <span>© 2026 WinterArc</span>
      </footer>
    </main>
  );
}

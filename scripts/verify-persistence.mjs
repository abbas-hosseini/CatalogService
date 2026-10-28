// Optional durability check. Requires Node.js 18+ and a JDK 27 JAVA_HOME.
// Runs two isolated instances of the packaged application; real data is untouched.
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { access, mkdtemp, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const jar = join(project, 'target', 'webapp-0.0.1-SNAPSHOT.jar');
await access(jar);
if (!process.env.JAVA_HOME) throw new Error('Set JAVA_HOME to JDK 27 first.');
const java = join(process.env.JAVA_HOME.trim(), 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
const directory = await mkdtemp(join(project, 'target', 'persistence-check-'));
const environment = { ...process.env };
delete environment.APP_DB_URL;
delete environment.APP_DB_USERNAME;
delete environment.APP_DB_PASSWORD;
let ownedProcess;

async function stop() {
    if (!ownedProcess || ownedProcess.exitCode !== null || ownedProcess.signalCode !== null) return;
    const exited = once(ownedProcess, 'exit');
    ownedProcess.kill('SIGKILL');
    await exited;
    ownedProcess = undefined;
}

try {
    for (let pass = 1; pass <= 2; pass++) {
        let log = '';
        ownedProcess = spawn(java, ['-jar', jar, '--server.port=0'], { cwd: directory, env: environment, windowsHide: true });
        const origin = await new Promise((resolveOrigin, reject) => {
            const timer = setTimeout(() => reject(new Error('Application startup timed out.')), 30000);
            const onOutput = chunk => {
                log += chunk.toString();
                const port = log.match(/Tomcat started on port (\d+)/)?.[1];
                if (port && log.includes('Started WebApplication in')) {
                    clearTimeout(timer);
                    resolveOrigin(`http://127.0.0.1:${port}`);
                }
            };
            ownedProcess.stdout.on('data', onOutput);
            ownedProcess.stderr.on('data', onOutput);
            ownedProcess.once('error', error => { clearTimeout(timer); reject(error); });
            ownedProcess.once('exit', code => { clearTimeout(timer); reject(new Error(`Application exited (${code}). ${log}`)); });
        });
        if (pass === 1) {
            const response = await fetch(`${origin}/api/factors`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ code: 'PERSISTENCE-CHECK', title: 'Durability verification' })
            });
            if (response.status !== 201) throw new Error(`Create failed: ${await response.text()}`);
            await response.json();
        } else {
            const response = await fetch(`${origin}/api/factors`);
            const rows = await response.json();
            if (!response.ok || rows.length !== 1 || rows[0].code !== 'PERSISTENCE-CHECK')
                throw new Error(`Persisted data was not recovered: ${JSON.stringify(rows)}`);
            console.log('PASS: Saved data survived immediate process termination and restart.');
        }
        await stop();
        await writeFile(join(directory, `run-${pass}.log`), log);
    }
} finally {
    await stop();
}

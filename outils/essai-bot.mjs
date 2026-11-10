// Faux joueur pour l'essai automatique de vorace-item (CI) : il se connecte, dit ce qu'il tient en main, puis frappe
// le cochon posé devant lui. Avec l'épée de vorace-item, un seul coup doit suffire (à mains nues il en faudrait dix).
// Ensuite il ouvre la forge, choisit l'épée et clique « Forger » : une fois sans matériaux (refus), une fois avec.
// Enfin il ouvre l'atelier, y pose une épée Rare et un rubis, clique « Sertir » et referme : l'épée doit lui revenir
// avec sa gemme.
import mineflayer from 'mineflayer';

const duree = Number(process.argv[2] || 40) * 1000;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Testeur', auth: 'offline' });

bot.once('spawn', () => {
  console.log('BOT CONNECTE ' + bot.version + ' en ' + bot.entity.position);
  // 8 secondes plus tard : l'épée a été donnée et le cochon posé par le script d'essai
  setTimeout(async () => {
    const objet = bot.heldItem;
    // ce que le jeu a reçu pour l'apparence : le modèle composé du kit et une chaîne par pièce
    const apparence = (objet?.components || []).filter((c) => c.type === 'item_model' || c.type === 'custom_model_data');
    console.log('BOT OBJET ' + (objet ? objet.name + ' ' + JSON.stringify(apparence) : 'rien en main'));
    const cochon = bot.nearestEntity((e) => e.name === 'pig');
    if (!cochon) { console.log('BOT PAS DE COCHON'); return; }
    try {
      await bot.lookAt(cochon.position.offset(0, 0.4, 0), true);
      bot.attack(cochon);
      console.log('BOT ATTAQUE cochon à ' + bot.entity.position.distanceTo(cochon.position).toFixed(1) + ' bloc(s)');
    } catch (e) {
      console.log('BOT ERREUR attaque ' + e);
    }
  }, 8000);
  // la forge : /vitem forge, clic sur l'épée (première case), puis sur « Forger » (case 49)
  setTimeout(() => bot.chat('/vitem forge'), 14000);
  setTimeout(() => {
    console.log('BOT FIN');
    bot.quit();
    setTimeout(() => process.exit(0), 500);
  }, duree);
});
let fenetres = 0;
const cliquer = (c, quand) => setTimeout(() => {
  if (!bot.currentWindow) { console.log('BOT PAS DE MENU pour la case ' + c); return; }
  bot.clickWindow(c, 0, 0).catch((e) => console.log('BOT ERREUR clic ' + e));
}, quand);
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
let atelier = false;
bot.on('windowOpen', (w) => {
  fenetres++;
  console.log('BOT MENU ' + fenetres + ' : ' + JSON.stringify(w.title).slice(0, 200) + ' (' + w.slots.filter((x, i) => x && i < w.inventoryStart).length + ' cases remplies)');
  if (atelier) { sertir(w); return; }
  if (fenetres === 1) cliquer(0, 600);        // l'épée modulaire
  else if (fenetres === 2) cliquer(49, 600);  // « Forger », sans matériaux : refus
});
// atelier : l'épée Rare (elle a un emplacement de gemme libre) dans la case 11, le rubis dans la case 15, puis « Sertir » (33)
async function sertir(w) {
  try {
    const cases = w.slots.map((x, i) => [i, x]).filter(([i, x]) => x && i >= w.inventoryStart);
    const epee = cases.find(([, x]) => JSON.stringify(x).includes('Emplacement de gemme libre'));
    const rubis = cases.find(([, x]) => JSON.stringify(x).includes('Rubis'));
    if (!epee || !rubis) { console.log('BOT ATELIER : épée ou rubis introuvable dans l\'inventaire'); return; }
    await pause(500); await bot.clickWindow(epee[0], 0, 0);
    await pause(300); await bot.clickWindow(11, 0, 0);
    await pause(300); await bot.clickWindow(rubis[0], 0, 0);
    await pause(300); await bot.clickWindow(15, 0, 0);
    await pause(600); await bot.clickWindow(33, 0, 0);
    await pause(600); bot.closeWindow(w);
    await pause(800);
    const sertie = bot.inventory.items().find((x) => { const t = JSON.stringify(x); return t.includes('Rubis taill') && t.includes('item_model'); });
    console.log(sertie ? 'BOT SERTI OK : l\'épée est revenue avec sa gemme' : 'BOT SERTI ECHEC : aucune épée sertie dans l\'inventaire');
  } catch (e) {
    console.log('BOT ERREUR atelier ' + e);
  }
}
bot.on('message', (m) => {
  const texte = m.toString();
  console.log('BOT MESSAGE ' + texte);
  // après le refus, le script d'essai donne les matériaux : second clic sur « Forger »
  if (texte.includes('Il te manque')) { console.log('BOT REFUS'); cliquer(49, 4000); }
  if (texte.includes('Tu as fabriqué')) { console.log('BOT FORGE OK'); setTimeout(() => { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); }, 500); }
  // le script d'essai a donné l'épée Rare et le rubis, puis dit « ATELIER »
  if (texte.includes('ATELIER') && !atelier) { atelier = true; bot.chat('/vitem atelier'); }
  if (texte.includes('Gemme sertie')) console.log('BOT GEMME SERTIE');
});
bot.on('kicked', (r) => console.log('BOT EXPULSE ' + JSON.stringify(r)));
bot.on('error', (e) => console.log('BOT ERREUR ' + e));

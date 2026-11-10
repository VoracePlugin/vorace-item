// Faux joueur pour l'essai automatique de vorace-item (CI) : il se connecte, dit ce qu'il tient en main, puis frappe
// le cochon posé devant lui. Avec l'épée de vorace-item, un seul coup doit suffire (à mains nues il en faudrait dix).
// Ensuite il ouvre la forge, choisit l'épée et clique « Forger » : une fois sans matériaux (refus), une fois avec.
import mineflayer from 'mineflayer';

const duree = Number(process.argv[2] || 40) * 1000;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Testeur', auth: 'offline' });

bot.once('spawn', () => {
  console.log('BOT CONNECTE ' + bot.version + ' en ' + bot.entity.position);
  // 8 secondes plus tard : l'épée a été donnée et le cochon posé par le script d'essai
  setTimeout(async () => {
    const objet = bot.heldItem;
    console.log('BOT OBJET ' + (objet ? JSON.stringify(objet).slice(0, 3000) : 'rien en main'));
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
bot.on('windowOpen', (w) => {
  fenetres++;
  console.log('BOT MENU ' + fenetres + ' : ' + JSON.stringify(w.title).slice(0, 200) + ' (' + w.slots.filter((x, i) => x && i < w.inventoryStart).length + ' cases remplies)');
  if (fenetres === 1) cliquer(0, 600);        // l'épée modulaire
  else if (fenetres === 2) cliquer(49, 600);  // « Forger », sans matériaux : refus
});
bot.on('message', (m) => {
  const texte = m.toString();
  console.log('BOT MESSAGE ' + texte);
  // après le refus, le script d'essai donne les matériaux : second clic sur « Forger »
  if (texte.includes('Il te manque')) { console.log('BOT REFUS'); cliquer(49, 4000); }
  if (texte.includes('Tu as fabriqué')) console.log('BOT FORGE OK');
});
bot.on('kicked', (r) => console.log('BOT EXPULSE ' + JSON.stringify(r)));
bot.on('error', (e) => console.log('BOT ERREUR ' + e));

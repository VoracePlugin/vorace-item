// Faux joueur pour l'essai automatique de vorace-item (CI) : il se connecte, dit ce qu'il tient en main, frappe le
// cochon posé devant lui avec l'épée de vorace-item, puis dit ce que l'épée est devenue : le jeu veut l'user d'un point,
// vorace-item doit le remplacer par sa propre usure (la barre de durabilité de l'objet bouge).
import mineflayer from 'mineflayer';

const duree = Number(process.argv[2] || 40) * 1000;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Testeur', auth: 'offline' });

const composants = (objet, types) => (objet?.components || []).filter((c) => types.includes(c.type));

bot.once('spawn', () => {
  console.log('BOT CONNECTE ' + bot.version + ' en ' + bot.entity.position);
  // 8 secondes plus tard : l'épée a été donnée et le cochon posé par le script d'essai
  setTimeout(async () => {
    const objet = bot.heldItem;
    // ce que le jeu a reçu pour l'apparence : le modèle composé du kit et une chaîne par pièce
    console.log('BOT OBJET ' + (objet ? objet.name + ' ' + JSON.stringify(composants(objet, ['item_model', 'custom_model_data'])) : 'rien en main'));
    console.log('BOT AVANT ' + JSON.stringify(composants(objet, ['damage', 'max_damage'])));
    const cochon = bot.nearestEntity((e) => e.name === 'pig');
    if (!cochon) { console.log('BOT PAS DE COCHON'); return; }
    try {
      await bot.lookAt(cochon.position.offset(0, 0.4, 0), true);
      bot.attack(cochon);
      console.log('BOT ATTAQUE cochon à ' + bot.entity.position.distanceTo(cochon.position).toFixed(1) + ' bloc(s)');
    } catch (e) {
      console.log('BOT ERREUR attaque ' + e);
    }
    setTimeout(() => console.log('BOT APRES ' + JSON.stringify(composants(bot.heldItem, ['damage', 'max_damage']))), 2000);
  }, 8000);
  setTimeout(() => {
    console.log('BOT FIN');
    bot.quit();
    setTimeout(() => process.exit(0), 500);
  }, duree);
});
bot.on('message', (m) => console.log('BOT MESSAGE ' + m.toString()));
bot.on('kicked', (r) => console.log('BOT EXPULSE ' + JSON.stringify(r)));
bot.on('error', (e) => console.log('BOT ERREUR ' + e));

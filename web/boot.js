import { cloud } from './cloud-client.js';
window.PaisaWeb=true;
try{
 await cloud.boot();window.PaisaCloud=cloud;
 document.body.classList.add('web-companion');
 await import('./app-v3.js');
 if(cloud.pairId)window.paisaGoSync?.();
 if('serviceWorker' in navigator)navigator.serviceWorker.register('./sw.js',{scope:'./'}).catch(()=>{});
}catch(error){document.getElementById('main').textContent='Paisa could not open browser storage: '+error.message;}

import{i as e,n as t}from"./lit-html-BftHCVJg.js";var n=[{id:`scryfall`,name:`Scryfall (Magic)`,url:`https://api.scryfall.com/cards/named?exact=Opt`,fallback:`Sin plan B: es la fuente principal de Magic.`},{id:`scryfall-search`,name:`Scryfall búsqueda`,url:`https://api.scryfall.com/cards/search?q=lightning+bolt&unique=prints`,fallback:`Sin plan B: es la fuente principal de Magic.`},{id:`cardkingdom`,name:`Card Kingdom (lista de precios)`,url:`https://api.cardkingdom.com/api/v2/pricelist`,init:{method:`HEAD`},fallback:`Workflow diario que publica la lista achicada en docs/app/data/ck-prices.json.`},{id:`tcgdex`,name:`TCGdex (Pokémon)`,url:`https://api.tcgdex.net/v2/en/cards?name=pikachu`,fallback:`Usar pokemontcg.io.`},{id:`pokemontcg`,name:`pokemontcg.io (Pokémon, respaldo)`,url:`https://api.pokemontcg.io/v2/cards?pageSize=1`,fallback:`Usar TCGdex.`},{id:`ygoprodeck`,name:`YGOPRODeck (Yu-Gi-Oh!)`,url:`https://db.ygoprodeck.com/api/v7/cardinfo.php?name=Dark%20Magician`,fallback:`Workflow diario como Card Kingdom.`},{id:`lemonsqueezy`,name:`Lemon Squeezy (verificar licencia)`,url:`https://api.lemonsqueezy.com/v1/licenses/validate`,init:{method:`POST`,headers:{Accept:`application/json`,"Content-Type":`application/x-www-form-urlencoded`},body:`license_key=prueba-cors`},fallback:`Cloudflare Worker gratis que solo reenvía esta llamada.`}];async function r(e,t=fetch){let n=performance.now();try{let r=await t(e.url,{...e.init,cache:`no-store`});return{id:e.id,ok:!0,status:r.status,ms:Math.round(performance.now()-n)}}catch(t){return{id:e.id,ok:!1,error:t instanceof Error?t.message:String(t),ms:Math.round(performance.now()-n)}}}var i=new Map;function a(){return e`
    <h1>Prueba de fuentes desde el navegador</h1>
    <p>Origen: <code>${location.origin}</code></p>
    <table>
      <thead><tr><th>Fuente</th><th>Resultado</th><th>Si no funciona</th></tr></thead>
      <tbody>
        ${n.map(t=>{let n=i.get(t.id),r=n?n.ok?`Funciona (HTTP ${n.status}, ${n.ms} ms)`:`Bloqueada (${n.error})`:`…`;return e`<tr class=${n?n.ok?`ok`:`bad`:``}>
            <td>${t.name}</td><td data-id=${t.id}>${r}</td><td>${t.fallback}</td>
          </tr>`})}
      </tbody>
    </table>
    <pre id="json">${i.size===n.length?JSON.stringify([...i.values()],null,2):``}</pre>
  `}var o=document.getElementById(`app`);t(a(),o);for(let e of n)r(e).then(n=>{i.set(e.id,n),t(a(),o)});
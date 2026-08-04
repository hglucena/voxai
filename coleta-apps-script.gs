/**
 * VoxAI — coleta opt-in do teste de triagem.
 *
 * Recebe o POST do voxai/teste.html e grava:
 *   - uma linha na planilha (questionário + 20 medidas + grau), identificada por um ID aleatório;
 *   - o áudio da tarefa (vogal + contagem concatenadas) numa pasta do Drive, como <ID>.wav.
 * O ID é a única ligação entre a linha e o arquivo — não recebemos nome, e-mail nem IP.
 *
 * COMO PUBLICAR (repetir a cada edição, senão o /exec continua na versão antiga):
 *   Implantar → Nova implantação → Tipo: App da Web
 *   Executar como: Eu   ·   Quem tem acesso: Qualquer pessoa
 *
 * Na primeira execução o Apps Script pede autorização para Planilhas e Drive.
 */

// Deixe vazio para usar a planilha à qual este script está vinculado.
var PLANILHA_ID = "";
// A aba precisa estar vazia (ou não existir) na primeira execução: o cabeçalho mudou
// e ganhou as 20 medidas + áudio. Se houver uma aba antiga com outro formato, apague-a
// antes — o script para com erro em vez de gravar linhas desalinhadas.
var ABA = "respostas";
// Pasta do Drive onde os áudios são gravados (criada na primeira vez, se não existir).
var PASTA_AUDIOS = "VoxAI - audios";

var FEATURES = [
  "f0media", "f0dp", "f0q1", "f0CV", "jitterLoc", "jitterABS", "jitterPPQ5",
  "shimmerLoc", "shimmerdB", "CPP", "CPPS", "H1A3", "GNE1000HZ", "GNE2000HZ",
  "GNE3000HZ", "Hfno", "HNRmedia", "HNRdp", "HNRD", "SNL100_3000HZ"
];

var COLUNAS = ["data", "id", "genero", "idade", "foraFaixa", "profissaoUsaVoz",
               "queixaVocal", "esforcoVocal", "rouquidao",
               "grau", "grau_rotulo", "score_GG"]
              .concat(FEATURES)
              .concat(["audio_arquivo", "audio_link"]);

function doPost(e) {
  try {
    var dados = JSON.parse(e.postData.contents);
    var aba = pegaAba_();
    var id = String(dados.id || Utilities.getUuid()).replace(/[^A-Za-z0-9_-]/g, "");

    var audioNome = "";
    var audioLink = "";
    if (dados.audio_wav_b64) {
      var arquivo = salvaAudio_(id, dados.audio_wav_b64);
      audioNome = arquivo.getName();
      audioLink = arquivo.getUrl();
    }

    var feats = dados.features || {};
    var linha = [
      new Date(), id, dados.genero || "", dados.idade,
      dados.foraFaixa ? "sim" : "nao",
      dados.profissaoUsaVoz || "", dados.queixaVocal || "",
      dados.esforcoVocal || "", dados.rouquidao || "",
      dados.grau, dados.grau_rotulo || "", dados.score_GG
    ];
    FEATURES.forEach(function (k) {
      var v = feats[k];
      linha.push(v === null || v === undefined ? "" : v);
    });
    linha.push(audioNome, audioLink);

    aba.appendRow(linha);
    return json_({ ok: true, id: id, audio: audioNome });
  } catch (err) {
    return json_({ ok: false, erro: String(err) });
  }
}

function doGet() {
  return json_({ ok: true, servico: "voxai-coleta" });
}

/**
 * Aba de respostas, criando o cabeçalho na primeira vez.
 * Se a aba já existir com outro cabeçalho, para com erro em vez de anexar linhas
 * desalinhadas (foi o que aconteceria ao reaproveitar a aba antiga).
 */
function pegaAba_() {
  var ss = PLANILHA_ID ? SpreadsheetApp.openById(PLANILHA_ID)
                       : SpreadsheetApp.getActiveSpreadsheet();
  var aba = ss.getSheetByName(ABA) || ss.insertSheet(ABA);
  if (aba.getLastRow() === 0) {
    aba.appendRow(COLUNAS);
    aba.setFrozenRows(1);
    return aba;
  }
  var atual = aba.getRange(1, 1, 1, aba.getLastColumn()).getValues()[0];
  if (atual.join("|") !== COLUNAS.join("|")) {
    throw new Error("A aba '" + ABA + "' tem um cabecalho diferente do esperado. "
                  + "Renomeie-a ou troque a constante ABA, para nao misturar formatos.");
  }
  return aba;
}

/** Grava o WAV recebido em base64 na pasta de áudios e devolve o arquivo. */
function salvaAudio_(id, b64) {
  var pasta = pegaPasta_(PASTA_AUDIOS);
  var bytes = Utilities.base64Decode(b64);
  var blob = Utilities.newBlob(bytes, "audio/wav", id + ".wav");
  return pasta.createFile(blob);
}

function pegaPasta_(nome) {
  var it = DriveApp.getFoldersByName(nome);
  return it.hasNext() ? it.next() : DriveApp.createFolder(nome);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
                       .setMimeType(ContentService.MimeType.JSON);
}

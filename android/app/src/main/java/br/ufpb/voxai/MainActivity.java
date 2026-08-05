package br.ufpb.voxai;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * O app é uma casca em volta do site do VoxAI: o WebView abre
 * https://hglucena.github.io/voxai/ e todo o teste roda a partir de lá.
 *
 * A escolha é deliberada. O teste depende de dois serviços online (extração de
 * medidas e classificação), então o app nunca funcionaria offline mesmo se as
 * telas viessem empacotadas. Deixando o conteúdo no site, qualquer correção no
 * questionário ou no texto chega ao celular sem publicar um APK novo.
 *
 * O que o código nativo resolve, e o navegador não resolveria sozinho:
 *   - a permissão de microfone, que no WebView precisa ser concedida duas vezes
 *     (a do Android, para o app, e a do WebView, para a página);
 *   - o botão voltar do sistema, que aqui navega no histórico da página;
 *   - links para fora do domínio, que abrem no navegador em vez de prender o
 *     usuário dentro do app;
 *   - uma tela de "sem conexão" no lugar do erro genérico do Chromium.
 */
public class MainActivity extends AppCompatActivity {

    private static final String HOST = "hglucena.github.io";
    /** O app abre direto no teste: a página inicial do site é vitrine, não faz sentido aqui. */
    private static final String INICIO = "https://hglucena.github.io/voxai/teste.html";
    /**
     * Marca acrescentada ao User-Agent. É por ela que a página sabe que está rodando
     * dentro do app e esconde o que só faz sentido no site (o "Voltar ao site", o
     * "Sobre o projeto" e a seção que oferece o download deste mesmo APK).
     */
    private static final String MARCA_APP = "VoxAIApp/1";
    private static final int PEDIDO_MICROFONE = 1;

    private WebView web;
    /** Pedido de mic que veio da página e está esperando a resposta do Android. */
    private PermissionRequest pedidoPendente;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // A página toca os áudios de exemplo e liga o AudioContext no toque do botão;
        // sem isto o Android exigiria um gesto extra que o usuário não faz.
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " " + MARCA_APP);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        // O site já é responsivo; sem isto o WebView renderiza no modo "desktop".
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return abrirForaSePreciso(req.getUrl());
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError erro) {
                if (req.isForMainFrame()) {
                    mostraTelaSemConexao();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest pedido) {
                boolean querMic = false;
                for (String recurso : pedido.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(recurso)) {
                        querMic = true;
                    }
                }
                if (!querMic) {
                    pedido.deny();
                    return;
                }
                if (temPermissaoDeMicrofone()) {
                    pedido.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pedidoPendente = pedido;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO}, PEDIDO_MICROFONE);
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest pedido) {
                pedidoPendente = null;
            }
        });

        // O botão voltar anda no histórico da página enquanto houver para onde voltar.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            }
        });

        // A gravação da vogal e a contagem levam alguns segundos com a tela parada;
        // sem isto o celular pode apagar no meio da tarefa.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (estado == null) {
            web.loadUrl(INICIO);
        } else {
            web.restoreState(estado);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    public void onRequestPermissionsResult(int codigo, @NonNull String[] permissoes,
                                           @NonNull int[] resultados) {
        super.onRequestPermissionsResult(codigo, permissoes, resultados);
        if (codigo != PEDIDO_MICROFONE || pedidoPendente == null) {
            return;
        }
        boolean liberado = resultados.length > 0
                && resultados[0] == PackageManager.PERMISSION_GRANTED;
        if (liberado) {
            pedidoPendente.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pedidoPendente.deny();
            Toast.makeText(this, R.string.mic_negado, Toast.LENGTH_LONG).show();
        }
        pedidoPendente = null;
    }

    private boolean temPermissaoDeMicrofone() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Mantém no app a navegação dentro do site do VoxAI e joga o resto (repositório,
     * links das instituições, mailto) para o navegador ou para o app correspondente.
     */
    private boolean abrirForaSePreciso(Uri destino) {
        String host = destino.getHost();
        String esquema = destino.getScheme();
        boolean interno = "https".equals(esquema) && HOST.equals(host);
        if (interno) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, destino));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.link_sem_app, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /**
     * Substitui a página de erro do Chromium, que fala em DNS e cache, por um aviso
     * curto com um botão que recarrega o site.
     */
    private void mostraTelaSemConexao() {
        String html =
                "<!doctype html><meta charset='utf-8'>"
              + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
              + "<style>body{margin:0;min-height:100vh;display:flex;align-items:center;"
              + "justify-content:center;font-family:system-ui,sans-serif;background:#fff;"
              + "color:#1c2433;text-align:center;padding:32px;line-height:1.6}"
              + "h1{font-size:20px;margin:0 0 10px}p{color:#5a6473;margin:0 0 24px;font-size:15px}"
              + "a{display:inline-block;background:#4638a8;color:#fff;text-decoration:none;"
              + "font-weight:600;padding:12px 24px;border-radius:8px}</style>"
              + "<div><h1>Sem conexão</h1>"
              + "<p>O VoxAI precisa de internet para analisar a sua voz.<br>"
              + "Verifique a conexão e tente de novo.</p>"
              + "<a href='" + INICIO + "'>Tentar de novo</a></div>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    protected void onDestroy() {
        // Evita que o WebView continue segurando o microfone se a Activity morrer
        // com a gravação em andamento.
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

package com.emergency.patient.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.emergency.patient.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * ChatWebViewFragment — WebView-based chat interface.
 * 
 * Simple WebView implementation for chat functionality.
 * Avoids complex Firestore real-time sync in favor of web-based chat.
 */
public class ChatWebViewFragment extends Fragment {

    private static final String TAG = "ChatWebViewFragment";
    private static final String CHAT_URL = "https://resplendent-semifreddo-53bb35.netlify.app";

    private WebView webView;
    private LinearLayout loadingState;
    private LinearLayout errorState;
    private FrameLayout webViewContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_webview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        webViewContainer = view.findViewById(R.id.webViewContainer);
        loadingState = view.findViewById(R.id.loadingState);
        errorState = view.findViewById(R.id.errorState);
        FrameLayout btnBack = view.findViewById(R.id.btnBack);

        // Setup back button to navigate to home page
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() instanceof DashboardActivity) {
                    DashboardActivity activity = (DashboardActivity) getActivity();
                    activity.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new MedicationSafetyHomeFragment())
                            .commit();
                    
                    // Update bottom navigation to show home as selected
                    BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_navigation);
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.nav_home);
                    }
                }
            });
        }

        if (CHAT_URL == null || CHAT_URL.isEmpty() || CHAT_URL.equals("https://example.com/chat")) {
            // Show error if URL not configured
            loadingState.setVisibility(View.GONE);
            errorState.setVisibility(View.VISIBLE);
            TextView errorTitle = errorState.findViewById(android.R.id.text1);
            if (errorTitle != null) {
                errorTitle.setText("Chat URL not configured");
            }
            return;
        }

        // Show loading state immediately before loading URL
        loadingState.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);

        setupWebView();
        webView.loadUrl(CHAT_URL);
    }

    private void setupWebView() {
        webView = new WebView(requireContext());
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        
        // Enable JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Cache mode - prioritize cache for faster loading
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        
        // Enable database for better performance
        settings.setDatabaseEnabled(true);
        
        // User agent
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " Sanjivani/1.0");
        
        // Security: Disable file access
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        
        // Performance optimizations
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setLoadsImagesAutomatically(true);
        
        // Set WebViewClient for page lifecycle
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                loadingState.setVisibility(View.VISIBLE);
                errorState.setVisibility(View.GONE);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingState.setVisibility(View.GONE);
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                loadingState.setVisibility(View.GONE);
                errorState.setVisibility(View.VISIBLE);
                
                TextView errorTitle = errorState.findViewById(android.R.id.text1);
                TextView errorDesc = errorState.findViewById(android.R.id.text2);
                if (errorTitle != null) {
                    errorTitle.setText("Failed to load chat");
                }
                if (errorDesc != null) {
                    errorDesc.setText("Please check your internet connection and try again.");
                }
            }
            
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                // Handle HTTP errors silently
            }
        });

        // Set WebChromeClient for progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                // Can update progress bar if needed
            }
        });

        webViewContainer.addView(webView);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webViewContainer.removeView(webView);
            webView.destroy();
            webView = null;
        }
    }

    /**
     * Handle back press - navigate within WebView history if possible
     * @return true if back press was handled, false otherwise
     */
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }
}

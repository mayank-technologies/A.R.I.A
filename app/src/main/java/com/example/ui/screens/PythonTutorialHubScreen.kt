package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun PythonTutorialHubScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf(
        "📖 Step-by-Step Guide",
        "🐍 Python ARIA Code",
        "📡 Mobile Backend Sync",
        "👋 Flutter Onboarding",
        "⚡ Flutter Wake Word",
        "🌦️ Weather & WorkManager",
        "📅 Calendar & Reminders"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "A.R.I.A. Python & Flutter Hub",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Guides + Wake Word + Weather + Calendar Reminders",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Tab Bar
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = CyberCyan,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyberCyan
                )
            },
            modifier = Modifier
                .padding(bottom = 12.dp)
                .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) CyberCyan else TextSecondary
                        )
                    }
                )
            }
        }

        // Content Area
        when (selectedTab) {
            0 -> StepByStepGuideView()
            1 -> PythonCodeView(context)
            2 -> MobileSyncArchitectureView(context)
            3 -> FlutterOnboardingGuideView(context)
            4 -> FlutterWakeWordGuideView(context)
            5 -> FlutterWeatherWorkManagerGuideView(context)
            6 -> FlutterCalendarRemindersGuideView(context)
        }
    }
}

@Composable
fun StepByStepGuideView() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            GuideStepCard(
                stepNumber = "1",
                title = "Python Setup & Virtual Environment",
                description = "Sabse pehle Python 3.10+ install karo aur ek virtual environment banao taaki saare voice & AI packages clean rahein.",
                command = "python -m venv aria_env\nsource aria_env/bin/activate  # Windows: aria_env\\Scripts\\activate"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "2",
                title = "Required Libraries Install Karo",
                description = "ARIA ko bolne (TTS), sunne (STT), AI capabilities (Gemini / OpenAI API) aur web backend API ke liye packages install karo.",
                command = "pip install pyttsx3 speechrecognition requests google-generativeai flask flask-cors pvporcupine geolocator"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "3",
                title = "Wake Word Detection (Hey ARIA)",
                description = "Picovoice Porcupine use karke lightweight background wake word listener activate karo jo kam RAM aur zero battery use karta hai.",
                command = "# Python script in tab '🐍 Python ARIA Code' runs 24/7 listening for 'Hey Aria'"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "4",
                title = "Flask API Backend Serve Karo",
                description = "Mobile Flutter App se connect karne ke liye local network IP (e.g. 192.168.1.X:5000) par Flask API server chalao.",
                command = "python aria_backend.py --host=0.0.0.0 --port=5000"
            )
        }
    }
}

@Composable
fun GuideStepCard(stepNumber: String, title: String, description: String, command: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(CyberCyan, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = DeepSpace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = command,
                    color = ElectricEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PythonCodeView(context: Context) {
    val pythonScript = """
# =========================================================
# A.R.I.A. FULL PYTHON AI BACKEND & VOICE ENGINE (aria_backend.py)
# =========================================================

import os
import time
import requests
import pyttsx3
import speech_recognition as sr
import google.generativeai as genai
from flask import Flask, request, jsonify
from flask_cors import CORS

# 1. INITIALIZE TEXT-TO-SPEECH (TTS) ENGINE
engine = pyttsx3.init()
voices = engine.getProperty('voices')
# Select female/JARVIS style voice if available
for voice in voices:
    if "female" in voice.name.lower() or "zira" in voice.name.lower():
        engine.setProperty('voice', voice.id)
        break
engine.setProperty('rate', 175) # Voice speed

def speak(text):
    print(f"🤖 ARIA: {text}")
    engine.say(text)
    engine.runAndWait()

# 2. INITIALIZE GEMINI AI MODEL
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "YOUR_GEMINI_API_KEY_HERE")
genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel('gemini-1.5-flash')

def get_ai_response(prompt):
    try:
        sys_prompt = f"You are ARIA, a highly intelligent JARVIS-style assistant. Keep answers brief (max 2 sentences) for voice response. Query: {prompt}"
        response = model.generate_content(sys_prompt)
        return response.text.strip()
    except Exception as e:
        return f"Sorry Boss, I encountered an AI error: {e}"

# 3. SPEECH TO TEXT (STT) ENGINE
def listen_command():
    recognizer = sr.Recognizer()
    with sr.Microphone() as source:
        print("🎤 Listening for voice command...")
        recognizer.adjust_for_ambient_noise(source, duration=0.8)
        try:
            audio = recognizer.listen(source, timeout=5, phrase_time_limit=8)
            command = recognizer.recognize_google(audio, language="en-IN")
            print(f"👤 User: {command}")
            return command.lower()
        except sr.UnknownValueError:
            return ""
        except sr.RequestError:
            speak("Speech service is offline.")
            return ""
        except Exception:
            return ""

# 4. FLASK API FOR MOBILE APP SYNC
app = Flask(__name__)
CORS(app)

@app.route('/api/command', methods=['POST'])
def process_mobile_command():
    data = request.json or {}
    user_query = data.get("query", "")
    print(f"📱 Mobile Command Received: {user_query}")
    
    if "weather" in user_query.lower():
        reply = "Currently it is 28 degrees Celsius with clear skies, Boss."
    else:
        reply = get_ai_response(user_query)
        
    return jsonify({
        "status": "success",
        "reply": reply,
        "timestamp": time.time()
    })

if __name__ == "__main__":
    speak("A.R.I.A. Online and Systems Nominal.")
    app.run(host="0.0.0.0", port=5000, debug=True)
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "aria_backend.py (Flask API + Gemini AI + TTS)",
                    color = CyberCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .background(CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ARIA Python Backend", pythonScript)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Python Code Copied!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copy Code", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            CodeSnippetBox(code = pythonScript)
        }
    }
}

@Composable
fun MobileSyncArchitectureView(context: Context) {
    val httpSyncCode = """
// Flutter / Android Kotlin HTTP Client Request to Python ARIA Backend
suspend fun sendCommandToPythonBackend(userQuery: String): String {
    val client = OkHttpClient()
    val json = JSONObject().apply {
        put("query", userQuery)
    }
    val body = json.toString().toRequestBody("application/json".toMediaType())
    
    val request = Request.Builder()
        .url("http://192.168.1.100:5000/api/command") // Replace with your PC IP
        .post(body)
        .build()
        
    return withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val respJson = JSONObject(response.body?.string() ?: "{}")
                respJson.optString("reply", "No response from ARIA.")
            } else {
                "Backend server error ${'$'}{response.code}"
            }
        }
    }
}
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📡 Mobile ↔ Python Backend Sync Architecture",
                        color = NeonPurple,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. PC aur Mobile ko SAME Wi-Fi network par connect karo.\n" +
                                "2. Python Backend script `aria_backend.py` chalao (`host=0.0.0.0`, port `5000`).\n" +
                                "3. PC ka IP address check karo command prompt me (`ipconfig` ya `ifconfig`). E.g.: `192.168.1.100`.\n" +
                                "4. Flutter / Android app me URL set karo: `http://192.168.1.100:5000/api/command`.\n" +
                                "5. Mobile app voice record karke JSON query bhejegi aur ARIA backend wapas answer text dega jo Mobile par TTS bolega!",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "Mobile Kotlin / Flutter Sync Code",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = httpSyncCode)
        }
    }
}

@Composable
fun FlutterWakeWordGuideView(context: Context) {
    val pubspecCode = """
name: aria_assistant
description: "A JARVIS-style voice assistant with always-listening Picovoice Porcupine wake word."
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: ">=3.0.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter
  porcupine_flutter: ^3.0.1      # Picovoice Porcupine Wake Word Engine
  flutter_foreground_task: ^6.1.1 # Android Foreground Service with Persistent Notification
  speech_to_text: ^6.6.0          # Native STT after wake word trigger
  flutter_tts: ^3.8.5             # Text-to-Speech response voice
  http: ^1.2.0                    # Send voice commands to Python Flask API
  permission_handler: ^11.3.1     # Handle RECORD_AUDIO & Battery Exemption
  path_provider: ^2.1.2

dev_dependencies:
  flutter_test:
    sdk: flutter

flutter:
  uses-material-design: true
  assets:
    - assets/hey_aria.ppn        # Custom Picovoice Wake Word Model File
""".trimIndent()

    val manifestCode = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.aria_assistant">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:label="A.R.I.A."
        android:icon="@mipmap/ic_launcher">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name="com.pravera.flutter_foreground_task.service.ForegroundService"
            android:foregroundServiceType="microphone"
            android:exported="false" />
    </application>
</manifest>
""".trimIndent()

    val serviceCode = """
import 'dart:async';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:porcupine_flutter/porcupine_manager.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class AriaWakeWordTaskHandler extends TaskHandler {
  PorcupineManager? _porcupineManager;
  final SpeechToText _speechToText = SpeechToText();
  final FlutterTts _flutterTts = FlutterTts();
  bool _isProcessing = false;

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    print("🚀 ARIA Foreground Service Started!");

    try {
      _porcupineManager = await PorcupineManager.fromBuiltInKeywords(
        "YOUR_PICOVOICE_ACCESS_KEY",
        [BuiltInKeyword.PORCUPINE],
        _onWakeWordDetected,
      );
      await _porcupineManager?.start();
      print("🎤 Listening for Wake Word in Background...");
    } catch (e) {
      print("Error initializing Porcupine: ${'$'}e");
    }
  }

  void _onWakeWordDetected(int keywordIndex) async {
    if (_isProcessing) return;
    _isProcessing = true;
    print("⚡ WAKE WORD 'HEY ARIA' DETECTED!");

    FlutterForegroundTask.updateService(
      notificationTitle: "ARIA Active!",
      notificationText: "Listening for your command...",
    );

    await _flutterTts.speak("Yes Boss, I am listening.");
    await Future.delayed(const Duration(seconds: 1));

    bool available = await _speechToText.initialize();
    if (available) {
      _speechToText.listen(
        onResult: (result) async {
          if (result.finalResult) {
            String userCommand = result.recognizedWords;
            await _sendToBackendAndSpeak(userCommand);
            
            _isProcessing = false;
            FlutterForegroundTask.updateService(
              notificationTitle: "ARIA Background Service",
              notificationText: "Listening for 'Hey Aria'...",
            );
          }
        },
      );
    } else {
      _isProcessing = false;
    }
  }

  Future<void> _sendToBackendAndSpeak(String command) async {
    try {
      final response = await http.post(
        Uri.parse("http://192.168.1.100:5000/api/command"),
        headers: {"Content-Type": "application/json"},
        body: jsonEncode({"query": command}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        String reply = data["reply"] ?? "Done Boss.";
        await _flutterTts.speak(reply);
      }
    } catch (e) {
      await _flutterTts.speak("Could not reach Python backend server.");
    }
  }

  @override
  Future<void> onRepeatEvent(DateTime timestamp) async {}

  @override
  Future<void> onDestroy(DateTime timestamp) async {
    await _porcupineManager?.stop();
    await _porcupineManager?.delete();
  }
}
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚡ Flutter Always-Listening Wake Word Architecture",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Picovoice Porcupine Engine: Phone screen off/locked hone par bhi low-power local wake word detection karta hai ('Hey Aria').\n" +
                                "2. Android Foreground Service: Persistent Notification ke saath service background me live rehti hai bina OS kill kiye.\n" +
                                "3. Battery Optimization Exemption: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission se app Doze mode me sleep nahi hoti.\n" +
                                "4. Command Capture: Wake word milte hi Native Speech-To-Text active hota hai, command capture karta hai aur Python Flask API ko bhejta hai!",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml (Dependencies)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = pubspecCode)
        }

        item {
            Text(
                text = "2. AndroidManifest.xml (Permissions)",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = manifestCode)
        }

        item {
            Text(
                text = "3. lib/services/aria_background_service.dart",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = serviceCode)
        }
    }
}

@Composable
fun FlutterWeatherWorkManagerGuideView(context: Context) {
    val pubspecCode = """
name: aria_weather_assistant
description: "ARIA Voice Assistant with GPS Weather & Android WorkManager Periodic Sync"
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: ">=3.0.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter
  geolocator: ^10.1.0                 # Live GPS Latitude & Longitude
  http: ^1.2.0                        # OpenWeatherMap REST API calls
  workmanager: ^0.5.2                 # Android WorkManager for 30-60 min background sync
  flutter_local_notifications: ^17.1.0 # Local notifications on weather alert
  flutter_tts: ^3.8.5                 # Speak weather reports aloud
  flutter_dotenv: ^5.1.0              # Secure API Key loading from .env
  shared_preferences: ^2.2.2          # Storing last weather state for delta checks
  permission_handler: ^11.3.1         # Location & Notification Runtime Permissions

dev_dependencies:
  flutter_test:
    sdk: flutter

flutter:
  uses-material-design: true
  assets:
    - .env                             # Contains OPENWEATHER_API_KEY
""".trimIndent()

    val weatherServiceCode = """
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:geolocator/geolocator.dart';

class WeatherData {
  final String city;
  final double tempC;
  final String condition;
  final int humidity;
  final double windKmH;

  WeatherData({
    required this.city,
    required this.tempC,
    required this.condition,
    required this.humidity,
    required this.windKmH,
  });

  String toVoiceString() {
    return "Currently in ${'$'}city, it is ${'$'}{tempC.round()} degrees Celsius with ${'$'}condition. Humidity is ${'$'}humidity percent and wind speed is ${'$'}{windKmH.round()} kilometers per hour.";
  }
}

class WeatherService {
  static final String _apiKey = dotenv.env['OPENWEATHER_API_KEY'] ?? "";

  static Future<WeatherData?> fetchCurrentWeather() async {
    try {
      Position position = await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);

      final url = Uri.parse(
        'https://api.openweathermap.org/data/2.5/weather?lat=${'$'}{position.latitude}&lon=${'$'}{position.longitude}&units=metric&appid=${'$'}_apiKey'
      );

      final response = await http.get(url).timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return WeatherData(
          city: data['name'] ?? "your location",
          tempC: (data['main']['temp'] as num).toDouble(),
          condition: data['weather'][0]['description'] ?? "clear sky",
          humidity: data['main']['humidity'] ?? 0,
          windKmH: ((data['wind']['speed'] as num).toDouble()) * 3.6,
        );
      }
      return null;
    } catch (e) {
      print("Weather Fetch Exception: ${'$'}e");
      return null;
    }
  }
}
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🌦️ GPS Weather & WorkManager Architecture",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Live GPS Location: Geolocator se high-accuracy latitude & longitude fetch hote hain.\n" +
                                "2. OpenWeatherMap API: Real-time temperature (°C), weather condition, humidity aur wind speed fetch hota hai.\n" +
                                "3. Battery-Efficient WorkManager: Android WorkManager har 30-60 min me background sync karta hai without draining battery.\n" +
                                "4. Smart Alert Trigger: Weather rain/storm me badalne par ya temperature me >= 5°C shift aane par Android notification bhejta hai!",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml (Dependencies)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = pubspecCode)
        }

        item {
            Text(
                text = "2. lib/services/weather_service.dart",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = weatherServiceCode)
        }
    }
}

@Composable
fun FlutterCalendarRemindersGuideView(context: Context) {
    val pubspecCode = """
name: aria_calendar_reminders
description: "ARIA Voice Assistant with SQLite Event Database, Google Calendar Sync & Exact Alarm Notifications"
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: ">=3.0.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter
  sqflite: ^2.3.0                     # Local SQLite Database for Offline Reminders
  path: ^1.9.0                        # Database File Path helper
  flutter_local_notifications: ^17.1.0 # Exact Scheduled Alarm Notifications
  timezone: ^0.9.2                    # TimeZone support for exact alarm scheduling
  device_calendar: ^4.3.2             # Native Google Calendar / Device Calendar Sync
  flutter_tts: ^3.8.5                 # Speech response for event reminders
  speech_to_text: ^6.6.0              # Voice command input
  intl: ^0.19.0                       # Date & Time Formatting
  permission_handler: ^11.3.1         # Calendar & Alarm Permissions

dev_dependencies:
  flutter_test:
    sdk: flutter

flutter:
  uses-material-design: true
""".trimIndent()

    val dbCode = """
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class ReminderEvent {
  final int? id;
  final String title;
  final String dateTimeIso;
  final bool isSyncedToCalendar;

  ReminderEvent({
    this.id,
    required this.title,
    required this.dateTimeIso,
    this.isSyncedToCalendar = false,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'date_time_iso': dateTimeIso,
      'is_synced': isSyncedToCalendar ? 1 : 0,
    };
  }

  factory ReminderEvent.fromMap(Map<String, dynamic> map) {
    return ReminderEvent(
      id: map['id'],
      title: map['title'],
      dateTimeIso: map['date_time_iso'],
      isSyncedToCalendar: map['is_synced'] == 1,
    );
  }
}

class EventDatabase {
  static Database? _database;

  static Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  static Future<Database> _initDatabase() async {
    String dbPath = await getDatabasesPath();
    String path = join(dbPath, 'aria_reminders.db');

    return await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE reminder_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            date_time_iso TEXT NOT NULL,
            is_synced INTEGER DEFAULT 0
          )
        ''');
      },
    );
  }

  static Future<int> insertEvent(ReminderEvent event) async {
    final db = await database;
    return await db.insert('reminder_events', event.toMap());
  }

  static Future<List<ReminderEvent>> getAllEvents() async {
    final db = await database;
    final maps = await db.query('reminder_events', orderBy: 'date_time_iso ASC');
    return maps.map((map) => ReminderEvent.fromMap(map)).toList();
  }
}
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📅 SQLite Local Database & Exact Alarm Architecture",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Voice Command Parser: Natural Language regex se event ka title, date aur time auto-parse hota hai ('Remind me about X on Y at Z').\n" +
                                "2. SQLite Persistence (sqflite): Offline local database me reminders store hote hain.\n" +
                                "3. Exact Alarm Scheduling: `flutter_local_notifications` Exact Alarm Manager use karta hai jo Android Doze mode me bhi exact time par alarm trigger karta hai.\n" +
                                "4. Google Calendar Sync: Native `device_calendar` package se phone ke Google Calendar me sync offer kiya ja sakta hai!",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml (SQLite, Alarms & Calendar Dependencies)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = pubspecCode)
        }

        item {
            Text(
                text = "2. lib/database/event_database.dart (SQLite Schema)",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = dbCode)
        }
    }
}

@Composable
fun FlutterOnboardingGuideView(context: Context) {
    val pubspecCode = """
name: aria_assistant
description: "ARIA Assistant with Onboarding Name Flow and Shared Preferences Local Storage"
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: ">=3.0.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter
  shared_preferences: ^2.2.2   # Local key-value storage for user name
  flutter_tts: ^3.8.5          # Text-to-Speech welcome greeting
  google_fonts: ^6.1.0         # Futuristic HUD typography

dev_dependencies:
  flutter_test:
    sdk: flutter

flutter:
  uses-material-design: true
""".trimIndent()

    val mainCode = """
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/onboarding_screen.dart';
import 'screens/home_screen.dart';

void main() async {
  // Ensure Flutter engine bindings are initialized before async calls
  WidgetsFlutterBinding.ensureInitialized();

  // Check if user name is already stored in shared_preferences
  final prefs = await SharedPreferences.getInstance();
  final String? savedName = prefs.getString('user_name');

  runApp(AriaApp(initialUserName: savedName));
}

class AriaApp extends StatelessWidget {
  final String? initialUserName;

  const AriaApp({super.key, this.initialUserName});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'A.R.I.A. Assistant',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF030712), // DeepSpace background
        primaryColor: const Color(0xFF00F0FF),            // CyberCyan theme color
      ),
      // If name exists -> Home Screen, else -> Onboarding Screen
      home: (initialUserName != null && initialUserName!.isNotEmpty)
          ? HomeScreen(userName: initialUserName!)
          : const OnboardingScreen(),
    );
  }
}
""".trimIndent()

    val onboardingCode = """
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'home_screen.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final TextEditingController _nameController = TextEditingController();
  final FlutterTts _flutterTts = FlutterTts();
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _initTts();
  }

  void _initTts() async {
    await _flutterTts.setLanguage("en-US");
    await _flutterTts.setSpeechRate(0.5); // Natural speech rate
    await _flutterTts.setPitch(1.05);     // Futuristic HUD voice pitch
  }

  Future<void> _saveNameAndContinue() async {
    String name = _nameController.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Please enter your name Boss!"),
          backgroundColor: Colors.redAccent,
        ),
      );
      return;
    }

    setState(() => _isLoading = true);

    // 1. Save name to SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('user_name', name);

    // 2. Speak personalized welcome message
    String welcomeSpeech = "Nice to meet you, ${'$'}name! I'm A.R.I.A., your personal assistant.";
    await _flutterTts.speak(welcomeSpeech);

    // Wait slightly for voice greeting to initiate
    await Future.delayed(const Duration(milliseconds: 1200));

    if (!mounted) return;

    // 3. Navigate to Main Home Screen
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (context) => HomeScreen(userName: name),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const cyberCyan = Color(0xFF00F0FF);
    const deepSpace = Color(0xFF030712);
    const surfaceDark = Color(0xFF111827);

    return Scaffold(
      backgroundColor: deepSpace,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Futuristic ARIA Arc Reactor Icon Header
              Container(
                width: 90,
                height: 90,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: cyberCyan.withOpacity(0.12),
                  border: Border.all(color: cyberCyan, width: 2),
                  boxShadow: [
                    BoxShadow(
                      color: cyberCyan.withOpacity(0.3),
                      blurRadius: 20,
                      spreadRadius: 2,
                    ),
                  ],
                ),
                child: const Icon(Icons.blur_circular, color: cyberCyan, size: 50),
              ),

              const SizedBox(height: 32),

              const Text(
                "A.R.I.A. ONLINE",
                style: TextStyle(
                  color: cyberCyan,
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 2.0,
                ),
              ),

              const SizedBox(height: 8),

              const Text(
                "What should I call you?",
                style: TextStyle(
                  color: Colors.white70,
                  fontSize: 16,
                ),
              ),

              const SizedBox(height: 28),

              // Clean Futuristic Text Input Field
              TextField(
                controller: _nameController,
                style: const TextStyle(color: Colors.white, fontSize: 18),
                cursorColor: cyberCyan,
                decoration: InputDecoration(
                  hintText: "Enter your name...",
                  hintStyle: const TextStyle(color: Colors.white38),
                  filled: true,
                  fillColor: surfaceDark,
                  prefixIcon: const Icon(Icons.person_outline, color: cyberCyan),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14),
                    borderSide: BorderSide(color: cyberCyan.withOpacity(0.3)),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14),
                    borderSide: const BorderSide(color: cyberCyan, width: 2),
                  ),
                ),
              ),

              const SizedBox(height: 24),

              // Get Started / Continue Button
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _saveNameAndContinue,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: cyberCyan,
                    foregroundColor: deepSpace,
                    elevation: 6,
                    shape: RoundedCornerShape(12),
                  ),
                  child: _isLoading
                      ? const CircularProgressIndicator(color: deepSpace)
                      : const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              "Get Started",
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1.1,
                              ),
                            ),
                            SizedBox(width: 8),
                            Icon(Icons.arrow_forward_rounded, size: 20),
                          ],
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
""".trimIndent()

    val homeCode = """
import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';

class HomeScreen extends StatefulWidget {
  final String userName;

  const HomeScreen({super.key, required this.userName});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final FlutterTts _flutterTts = FlutterTts();

  @override
  void initState() {
    super.initState();
    _speakPersonalizedGreeting();
  }

  void _speakPersonalizedGreeting() async {
    int hour = DateTime.now().hour;
    String timeGreeting = "Good morning";
    if (hour >= 12 && hour < 17) {
      timeGreeting = "Good afternoon";
    } else if (hour >= 17) {
      timeGreeting = "Good evening";
    }

    // Personalized greeting speech using user's saved name
    String greeting = "${'$'}timeGreeting, ${'$'}{widget.userName}! How can I assist you today?";
    await _flutterTts.speak(greeting);
  }

  @override
  Widget build(BuildContext context) {
    const cyberCyan = Color(0xFF00F0FF);
    const deepSpace = Color(0xFF030712);

    return Scaffold(
      backgroundColor: deepSpace,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: Text(
          "Hello, ${'$'}{widget.userName}",
          style: const TextStyle(color: cyberCyan, fontWeight: FontWeight.bold),
        ),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.graphic_eq, color: cyberCyan, size: 80),
            const SizedBox(height: 16),
            Text(
              "A.R.I.A. is active and listening for ${'$'}{widget.userName}...",
              style: const TextStyle(color: Colors.white70, fontSize: 15),
            ),
          ],
        ),
      ),
    );
  }
}
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "👋 Flutter Onboarding & Personalized Name Greetings",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. First Launch Check: `SharedPreferences` se check hota hai ki `user_name` already saved hai ya nahi.\n" +
                                "2. Clean Futuristic Onboarding HUD: Pehli baar open hone par 'What should I call you?' screen dikhti hai.\n" +
                                "3. Voice Welcome Speech: Name enter karke 'Get Started' dabate hi ARIA bolti hai: 'Nice to meet you, [Name]! I'm ARIA.'\n" +
                                "4. Smart Personalized Greetings: App open karne par ARIA name ke saath wish karti hai ('Good morning, [Name]!').",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml (shared_preferences & flutter_tts)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = pubspecCode)
        }

        item {
            Text(
                text = "2. lib/main.dart (First Launch Routing Check)",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = mainCode)
        }

        item {
            Text(
                text = "3. lib/screens/onboarding_screen.dart (HUD Onboarding UI & Speech)",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = onboardingCode)
        }

        item {
            Text(
                text = "4. lib/screens/home_screen.dart (Personalized Greetings with Name)",
                color = NeonPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = homeCode)
        }
    }
}

@Composable
fun CodeSnippetBox(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = code,
            color = Color(0xFFE6EDE3),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

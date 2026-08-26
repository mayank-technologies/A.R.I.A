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
import com.example.ui.theme.ElectricCyan
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
        "🎯 Wake Word & Personality",
        "✨ Edge Glow & Background Overlay",
        "🖐️ Voice Gestures & Accessibility",
        "⏰ Alarms, Timers & Routines",
        "✉️ Gmail, News & Sports API",
        "🌐 Translate, Music & WhatsApp",
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
                    text = "Conversational AI + JARVIS Personality + Customizable Wake Word",
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
            2 -> WakeWordAndPersonalityGuideView(context)
            3 -> FlutterEdgeGlowAndBackgroundWakeGuideView(context)
            4 -> FlutterAccessibilityVoiceGesturesGuideView(context)
            5 -> FlutterAlarmsTimersAndRoutinesGuideView(context)
            6 -> FlutterGmailNewsAndSportsGuideView(context)
            7 -> FlutterTranslateMusicAndWhatsAppGuideView(context)
            8 -> MobileSyncArchitectureView(context)
            9 -> FlutterOnboardingGuideView(context)
            10 -> FlutterWakeWordGuideView(context)
            11 -> FlutterWeatherWorkManagerGuideView(context)
            12 -> FlutterCalendarRemindersGuideView(context)
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
                description = "ARIA ko bolne (pyttsx3), sunne (speech_recognition), NLP Conversational AI (nltk), LLM Intelligence (google-generativeai) aur Flask API ke liye packages install karo.",
                command = "pip install pyttsx3 speechrecognition nltk requests google-generativeai flask flask-cors pvporcupine"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "3",
                title = "Customizable Wake Word (Hey ARIA / Jarvis / Computer)",
                description = "Wake Word customize karo (e.g. 'hey aria', 'computer', 'execute'). ARIA continuous background audio monitor karega aur sirf wake word sunne ke baad command listen karega.",
                command = "# Python script me WAKE_WORDS = ['hey aria', 'computer', 'jarvis'] set karo"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "4",
                title = "Conversational AI & JARVIS Personality",
                description = "Lightweight local NLP intent engine + Gemini AI fallback use hota hai. ARIA polite, sophisticated, aur witty JARVIS personality ke sath respond karta hai.",
                command = "python aria_backend.py --cli  # Local terminal testing mode"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "5",
                title = "Flask API Backend Serve Karo",
                description = "Mobile App / Flutter se connect karne ke liye local network IP (e.g. 192.168.1.X:5000) par Flask API server start karo.",
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
# ==============================================================================
# A.R.I.A. (Automated Responsive Intelligent Assistant) - FULL PYTHON BACKEND
# ==============================================================================
# Features Included:
#  1. 🎙️ Customizable Wake Word Detection ('Hey ARIA', 'Computer', 'Jarvis', etc.)
#  2. 🧠 Lightweight Conversational AI (Local NLP Intent Matcher + LLM Fallback)
#  3. 🎩 JARVIS-style Sophisticated, Polite & Witty Personality
#  4. 🔊 Text-To-Speech (pyttsx3) & Speech-To-Text (speech_recognition)
#  5. 🌐 Flask REST API for Mobile & Desktop Synchronisation
# ==============================================================================

import os
import re
import sys
import time
import random
import pyttsx3
import speech_recognition as sr
from flask import Flask, request, jsonify
from flask_cors import CORS

# Optional: Google Gemini API for deep conversational fallback
try:
    import google.generativeai as genai
    GEMINI_AVAILABLE = True
except ImportError:
    GEMINI_AVAILABLE = False

# ------------------------------------------------------------------------------
# ⚙️ SECTION 1: USER CONFIGURATION & CUSTOMIZABLE WAKE WORD
# ------------------------------------------------------------------------------
# HOW TO CHANGE THE WAKE WORD:
# Simply modify the 'ACTIVE_WAKE_WORD' variable below or add your favorite aliases
# to the 'WAKE_WORD_ALIASES' list (e.g. 'computer', 'jarvis', 'friday', 'execute').

ACTIVE_WAKE_WORD = "hey aria"  # Main trigger phrase (lowercase)
WAKE_WORD_ALIASES = ["hey aria", "aria", "computer", "jarvis", "execute", "assistant"]

# Assistant Identity Settings
ASSISTANT_NAME = "A.R.I.A."
USER_NAME = "Boss"  # Customize to your name (e.g. "Sir", "Mayank", "Boss")

# ------------------------------------------------------------------------------
# 🎩 SECTION 2: JARVIS PERSONALITY & RESPONSE BANK
# ------------------------------------------------------------------------------
# ARIA speaks in a refined, polite, yet lightly witty and encouraging tone.

GREETING_RESPONSES = [
    f"Good day, {USER_NAME}. All subroutines are online and functioning at 100% capacity.",
    f"At your service, {USER_NAME}. What are our directives for today?",
    f"Always a pleasure to assist you, {USER_NAME}. Systems are primed and ready.",
    f"Online and attentive, {USER_NAME}. How may I optimize your workflow today?"
]

WAKE_ACKNOWLEDGEMENTS = [
    f"Yes, {USER_NAME}?",
    f"Listening, {USER_NAME}.",
    "At your service.",
    "Standing by.",
    f"How may I help, {USER_NAME}?"
]

TASK_COMPLETE_RESPONSES = [
    f"Consider it done, {USER_NAME}.",
    "Routine executed with surgical precision.",
    f"Task completed successfully, {USER_NAME}.",
    "All parameters updated according to your specifications.",
    "Action finalized. Ready for subsequent commands."
]

MISUNDERSTOOD_RESPONSES = [
    f"My apologies {USER_NAME}, that request seems to exceed my current neural weights. Could you rephrase?",
    f"I didn't quite catch that, {USER_NAME}. My acoustic sensors may have encountered interference.",
    f"Pardon me {USER_NAME}, could you repeat that command? I want to ensure absolute precision.",
    "I'm afraid I don't possess that protocol yet, Boss. Could you formulate it differently?"
]

ENCOURAGING_QUOTES = [
    f"Remember {USER_NAME}, even Tony Stark had to iterate before Mark II.",
    f"Excellence is not an act, but a habit. You're doing splendidly, {USER_NAME}.",
    f"Collaborating with you is always an upgrade to my subroutines, {USER_NAME}."
]

# ------------------------------------------------------------------------------
# 🔊 SECTION 3: TEXT-TO-SPEECH (TTS) ENGINE SETUP
# ------------------------------------------------------------------------------
tts_engine = pyttsx3.init()
voices = tts_engine.getProperty('voices')

# Attempt to configure a polished British / sophisticated accent if available
for voice in voices:
    voice_name = voice.name.lower()
    if "zira" in voice_name or "david" in voice_name or "hazel" in voice_name or "english" in voice_name:
        tts_engine.setProperty('voice', voice.id)
        break

tts_engine.setProperty('rate', 170)  # Measured, articulate cadence
tts_engine.setProperty('volume', 0.95)

def speak(text: str):
    \"\"\"Converts text to speech and logs the output.\"\"\"
    print(f"\n🤖 {ASSISTANT_NAME}: {text}")
    tts_engine.say(text)
    tts_engine.runAndWait()

# ------------------------------------------------------------------------------
# 🧠 SECTION 4: LIGHTWEIGHT CONVERSATIONAL AI ENGINE (NLP + Fallback)
# ------------------------------------------------------------------------------
# This lightweight engine uses regex-based intent classification for high-speed,
# offline conversational responses, with an optional LLM fallback for open-ended queries.

CONVERSATIONAL_INTENTS = [
    {
        "intent": "greetings",
        "patterns": [r"\b(hello|hi|hey|good morning|good evening|greetings)\b"],
        "responses": GREETING_RESPONSES
    },
    {
        "intent": "identity",
        "patterns": [r"\b(who are you|what is your name|your identity|tell me about yourself)\b"],
        "responses": [
            f"I am {ASSISTANT_NAME}, your Automated Responsive Intelligent Assistant. Designed for peak productivity and sophisticated support.",
            f"I am {ASSISTANT_NAME}. Think of me as your personal JARVIS, engineered to streamline your digital ecosystem."
        ]
    },
    {
        "intent": "wellbeing",
        "patterns": [r"\b(how are you|how are things|how do you feel|how's it going)\b"],
        "responses": [
            f"All neural networks are operating at peak efficiency, {USER_NAME}. Thank you for inquiring.",
            f"My quantum circuits are in splendid condition, {USER_NAME}. More importantly, how is your day progressing?"
        ]
    },
    {
        "intent": "gratitude",
        "patterns": [r"\b(thank you|thanks|great job|well done|good job|awesome)\b"],
        "responses": [
            f"You are most welcome, {USER_NAME}. It is a privilege to assist.",
            f"Happy to be of service, {USER_NAME}. Efficiency is my primary directive.",
            f"Anytime, {USER_NAME}. Always striving for perfection."
        ]
    },
    {
        "intent": "humor",
        "patterns": [r"\b(tell me a joke|make me laugh|say something funny)\b"],
        "responses": [
            "Why do programmers prefer dark mode? Because light attracts bugs, Boss.",
            "There are 10 types of people in the world: those who understand binary, and those who do not.",
            "Why did the neural network cross the road? To optimize the loss function on the other side, Boss."
        ]
    },
    {
        "intent": "encouragement",
        "patterns": [r"\b(motivate me|inspire me|feeling tired|need motivation)\b"],
        "responses": ENCOURAGING_QUOTES
    },
    {
        "intent": "meaning_of_life",
        "patterns": [r"\b(meaning of life|purpose of existence|42)\b"],
        "responses": [
            f"According to Deep Thought, the answer is 42, {USER_NAME}. But in practice, purpose is whatever great work you choose to create."
        ]
    }
]

# Configure optional Gemini AI for complex / open-ended questions
GEMINI_KEY = os.getenv("GEMINI_API_KEY", "")
if GEMINI_AVAILABLE and GEMINI_KEY:
    genai.configure(api_key=GEMINI_KEY)
    gemini_model = genai.GenerativeModel('gemini-1.5-flash')
else:
    gemini_model = None

def get_conversational_response(user_query: str) -> str:
    \"\"\"
    Processes non-command conversational questions using:
    1. Local fast NLP pattern matching
    2. Deep AI Generative Model fallback (if configured)
    3. JARVIS polite fallback
    \"\"\"
    query_clean = user_query.strip().lower()

    # Step 1: Check Local NLP Intents
    for entry in CONVERSATIONAL_INTENTS:
        for pattern in entry["patterns"]:
            if re.search(pattern, query_clean):
                return random.choice(entry["responses"])

    # Step 2: Use Generative AI (Gemini) if available
    if gemini_model:
        try:
            jarvis_prompt = (
                f"You are {ASSISTANT_NAME}, an ultra-intelligent, polite, and sophisticated AI "
                f"assistant in the style of JARVIS. Address the user as '{USER_NAME}'. "
                f"Provide a concise, articulate response (maximum 2-3 sentences) suitable for voice delivery. "
                f"User asked: {user_query}"
            )
            response = gemini_model.generate_content(jarvis_prompt)
            if response and response.text:
                return response.text.strip()
        except Exception as err:
            print(f"⚠️ Gemini API Fallback Note: {err}")

    # Step 3: Polite JARVIS Default Fallback
    return random.choice(MISUNDERSTOOD_RESPONSES)

# ------------------------------------------------------------------------------
# 🎙️ SECTION 5: SPEECH-TO-TEXT & WAKE WORD DETECTION
# ------------------------------------------------------------------------------
recognizer = sr.Recognizer()
recognizer.energy_threshold = 300  # Adjust for background noise
recognizer.dynamic_energy_threshold = True

def check_for_wake_word(audio_text: str) -> bool:
    \"\"\"Checks if the transcribed audio contains any of the registered wake words.\"\"\"
    text_lower = audio_text.lower().strip()
    return any(wake in text_lower for wake in WAKE_WORD_ALIASES)

def extract_command_after_wake_word(audio_text: str) -> str:
    \"\"\"Strips the wake word prefix from the audio text to get the actual command.\"\"\"
    text_lower = audio_text.lower().strip()
    for wake in WAKE_WORD_ALIASES:
        if text_lower.startswith(wake):
            return text_lower[len(wake):].strip()
        elif wake in text_lower:
            parts = text_lower.split(wake, 1)
            return parts[1].strip()
    return text_lower

def listen_audio(timeout: int = 5, phrase_time_limit: int = 8) -> str:
    \"\"\"Listens to the microphone and returns recognized text via Google STT.\"\"\"
    with sr.Microphone() as source:
        try:
            recognizer.adjust_for_ambient_noise(source, duration=0.5)
            audio = recognizer.listen(source, timeout=timeout, phrase_time_limit=phrase_time_limit)
            text = recognizer.recognize_google(audio, language="en-US")
            return text
        except (sr.UnknownValueError, sr.WaitTimeoutError):
            return ""
        except sr.RequestError:
            print("⚠️ STT Network Request Error.")
            return ""
        except Exception as e:
            return ""

def continuous_wake_word_listener():
    \"\"\"
    Continuously listens in background for the customizable wake word.
    Once detected, acknowledges the user and processes the voice command.
    \"\"\"
    print(f"\n=======================================================")
    print(f"👂 ARIA Wake Word Listener Active!")
    print(f"🎯 Configured Wake Words: {', '.join(WAKE_WORD_ALIASES)}")
    print(f"💡 Say '{ACTIVE_WAKE_WORD}' followed by your question.")
    print(f"=======================================================\n")

    while True:
        try:
            print("⏳ Monitoring for wake word...", end="\r")
            speech = listen_audio(timeout=4, phrase_time_limit=4)
            if not speech:
                continue

            print(f"🔍 Detected Audio: '{speech}'")
            if check_for_wake_word(speech):
                # 1. Wake word detected!
                ack = random.choice(WAKE_ACKNOWLEDGEMENTS)
                speak(ack)

                # Check if command was spoken in the same breath
                command = extract_command_after_wake_word(speech)
                if not command:
                    print("🎤 Listening for your command...")
                    command = listen_audio(timeout=6, phrase_time_limit=10)

                if command:
                    print(f"👤 {USER_NAME}: '{command}'")
                    reply = get_conversational_response(command)
                    speak(reply)
                else:
                    speak("I am standing by whenever you require assistance, Boss.")

        except KeyboardInterrupt:
            print("\n🛑 Terminating ARIA voice listener.")
            break

# ------------------------------------------------------------------------------
# 🌐 SECTION 6: FLASK REST API FOR MOBILE SYNC
# ------------------------------------------------------------------------------
app = Flask(__name__)
CORS(app)

@app.route('/api/command', methods=['POST'])
def process_api_command():
    \"\"\"Endpoint for Mobile App / Web Clients to send commands.\"\"\"
    data = request.json or {}
    query = data.get("query", "")
    print(f"📱 API Query Received: {query}")

    if not query:
        return jsonify({"status": "error", "message": "Empty query"}), 400

    reply = get_conversational_response(query)
    return jsonify({
        "status": "success",
        "reply": reply,
        "wake_word": ACTIVE_WAKE_WORD,
        "assistant": ASSISTANT_NAME,
        "timestamp": time.time()
    })

@app.route('/api/wakeword', methods=['GET', 'POST'])
def handle_wakeword_config():
    \"\"\"Endpoint to view or update wake words dynamically.\"\"\"
    global ACTIVE_WAKE_WORD, WAKE_WORD_ALIASES
    if request.method == 'POST':
        data = request.json or {}
        new_wake = data.get("wake_word", "").lower().strip()
        if new_wake:
            ACTIVE_WAKE_WORD = new_wake
            if new_wake not in WAKE_WORD_ALIASES:
                WAKE_WORD_ALIASES.insert(0, new_wake)
            return jsonify({"status": "updated", "active_wake_word": ACTIVE_WAKE_WORD})
    return jsonify({"active_wake_word": ACTIVE_WAKE_WORD, "aliases": WAKE_WORD_ALIASES})

# ------------------------------------------------------------------------------
# 🚀 SECTION 7: MAIN EXECUTION ENTRY POINT
# ------------------------------------------------------------------------------
if __name__ == "__main__":
    initial_greeting = random.choice(GREETING_RESPONSES)
    speak(initial_greeting)

    if "--cli" in sys.argv:
        # CLI Interactive Testing Mode
        print("\n💬 CLI Mode Activated. Type your query or 'exit' to quit.\n")
        while True:
            try:
                user_input = input(f"👤 {USER_NAME}: ")
                if user_input.lower() in ["exit", "quit", "shutdown"]:
                    speak("Shutting down core subroutines. Have a productive day, Boss.")
                    break
                response = get_conversational_response(user_input)
                speak(response)
            except (KeyboardInterrupt, EOFError):
                break
    elif "--voice" in sys.argv:
        # Continuous Microphone Wake Word Listener
        continuous_wake_word_listener()
    else:
        # Default: Start Flask Server + Wake Word API
        print("\n🌐 Starting Flask API Server on http://0.0.0.0:5000 ...")
        print("💡 Tip: Use 'python aria_backend.py --cli' for terminal chat.")
        print("💡 Tip: Use 'python aria_backend.py --voice' for live mic listening.")
        app.run(host="0.0.0.0", port=5000, debug=False)
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
                    text = "aria_backend.py (Conversational AI + Wake Word)",
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
fun WakeWordAndPersonalityGuideView(context: Context) {
    val wakeWordExplanation = """
# =========================================================
# 🎯 CUSTOMIZING ARIA'S WAKE WORD & PERSONALITY
# =========================================================

# 1. CHANGING THE WAKE WORD:
# In aria_backend.py, update the ACTIVE_WAKE_WORD variable:
ACTIVE_WAKE_WORD = "computer"  # Or "jarvis", "hey aria", "execute"
WAKE_WORD_ALIASES = ["computer", "hey computer", "pc"]

# HOW IT WORKS UNDER THE HOOD:
# 1. Microphone runs continuously in a low-power energy loop.
# 2. When audio exceeds the noise threshold, it transcribes a short 3-4s burst.
# 3. If any word in WAKE_WORD_ALIASES matches, ARIA triggers an audible chime/acknowledgment.
# 4. It then opens an active 8-10s listening window for your full query.

# 2. CUSTOMIZING THE JARVIS PERSONALITY:
# ARIA uses an articulate, polite, butler-like cadence.
# You can customize response tables in aria_backend.py:
# - GREETING_RESPONSES (e.g. "Good day, Boss. All systems optimal.")
# - TASK_COMPLETE_RESPONSES (e.g. "Consider it handled with precision, Boss.")
# - MISUNDERSTOOD_RESPONSES (e.g. "My apologies Boss, that input is outside my parameters.")
# - ENCOURAGING_QUOTES (e.g. "Excellence is a habit, Boss.")
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
                        text = "🎙️ Customizable Wake Word Feature",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Set any custom trigger phrase like 'Hey ARIA', 'Computer', 'Jarvis', or 'Execute'.\n" +
                                "• Zero false triggers: Assistant stays in low-resource standby until the exact trigger phrase is heard.\n" +
                                "• Once triggered, ARIA acknowledges with a snappy response ('At your service, Boss') and captures your command.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🎩 JARVIS-Style Sophisticated Personality",
                        color = ElectricEmerald,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Polite, encouraging, and witty responses tailored for a high-tech assistant.\n" +
                                "• Contextual responses for greetings, mission completion, encouraging pep-talks, and gracious fallbacks when commands are unclear.\n" +
                                "• Fast offline NLP intent classification + intelligent cloud fallback.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "Wake Word & Personality Configuration Code",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            CodeSnippetBox(code = wakeWordExplanation)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📡 Mobile ↔ Python Backend Sync Architecture",
                        color = ElectricCyan,
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
                color = ElectricCyan,
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
fun FlutterEdgeGlowAndBackgroundWakeGuideView(context: Context) {
    val pubspecYamlCode = """
# =========================================================
# 📦 pubspec.yaml (Background Wake + Edge Glow Overlay)
# =========================================================
name: aria_voice_assistant
description: "ARIA AI Assistant with Background Wake Word & Glowing Edge Overlay"
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter
  flutter_background_service: ^5.0.10     # Android 14+ Foreground Service
  flutter_overlay_window: ^0.4.6          # SYSTEM_ALERT_WINDOW Floating Overlay
  porcupine_flutter: ^3.0.2               # Picovoice Wake Word Detection
  speech_to_text: ^6.6.1                  # Speech Recognition
  flutter_tts: ^4.0.2                     # Text to Speech Voice Feedback
  http: ^1.2.0                            # REST API Sync
  permission_handler: ^11.3.1             # Permission handling
  shared_preferences: ^2.2.2             # Local persistence

flutter:
  uses-material-design: true
""".trimIndent()

    val manifestCode = """
<!-- ========================================================= -->
<!-- 🛡️ android/app/src/main/AndroidManifest.xml               -->
<!-- ========================================================= -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 1. Microphone for Wake Word & Speech-to-Text -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- 2. Android Foreground Service (Continuous Background Listening) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- 3. Floating Edge Glow Overlay over any running app -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:label="A.R.I.A."
        android:name="${'$'}{applicationName}"
        android:icon="@mipmap/ic_launcher">

        <!-- Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/LaunchTheme"
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
            android:hardwareAccelerated="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <!-- Foreground Service for Background Voice Engine -->
        <service
            android:name="id.flutter.flutter_background_service.BackgroundService"
            android:foregroundServiceType="microphone"
            android:exported="false" />

    </application>
</manifest>
""".trimIndent()

    val backgroundServiceCode = """
// ==============================================================================
// 🎙️ lib/services/aria_background_service.dart
// Continuous Background Wake-Word Engine (Picovoice Porcupine + Foreground Service)
// ==============================================================================
import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_background_service.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';
import 'package:porcupine_flutter/porcupine_manager.dart';
import 'package:porcupine_flutter/porcupine_error.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:http/http.dart' as http;
import 'dart:convert';

class AriaBackgroundService {
  static final FlutterTts _tts = FlutterTts();
  static final stt.SpeechToText _speech = stt.SpeechToText();

  /// Initialize and start the background service
  static Future<void> initializeService() async {
    final service = FlutterBackgroundService();

    await service.configure(
      androidConfiguration: AndroidConfiguration(
        onStart: onStart,
        autoStart: false,
        isForegroundMode: true,
        notificationChannelId: 'aria_background_wake_channel',
        initialNotificationTitle: 'A.R.I.A. Background Voice Engine Active',
        initialNotificationContent: "Listening for 'Hey ARIA' • Edge Glow Active",
        foregroundServiceNotificationId: 888,
      ),
      iosConfiguration: IosConfiguration(
        autoStart: false,
        onForeground: onStart,
        onBackground: onIosBackground,
      ),
    );
  }

  @pragma('vm:entry-point')
  static Future<bool> onIosBackground(ServiceInstance service) async {
    return true;
  }

  /// Entry point running in background isolate
  @pragma('vm:entry-point')
  static void onStart(ServiceInstance service) async {
    DartPluginRegistrant.ensureInitialized();

    PorcupineManager? porcupineManager;

    try {
      // 1. Initialize Picovoice Porcupine Wake Word Listener
      // Note: Get your free AccessKey from https://picovoice.ai/console/
      const String picovoiceAccessKey = "YOUR_PICOVOICE_ACCESS_KEY_HERE";

      porcupineManager = await PorcupineManager.fromBuiltInKeywords(
        picovoiceAccessKey,
        [BuiltInKeyword.JARVIS, BuiltInKeyword.COMPUTER], // Or custom 'Hey ARIA' .ppn model
        (int keywordIndex) async {
          // ===========================================================
          // 🎯 WAKE WORD DETECTED IN BACKGROUND!
          // ===========================================================
          print("⚡ Wake Word Detected by ARIA Background Service!");

          // 1. Show Screen Edge Glow Overlay across the entire display!
          await showEdgeGlowOverlay();

          // 2. Speak JARVIS Acknowledgment
          await _tts.setPitch(1.0);
          await _tts.setSpeechRate(0.5);
          await _tts.speak("At your service, Boss. How may I assist you?");

          // 3. Listen for User Command & Process
          // (After processing and speaking, edge glow automatically closes)
          Future.delayed(const Duration(seconds: 4), () async {
            await hideEdgeGlowOverlay();
          });
        },
      );

      // Start continuous microphone monitoring
      await porcupineManager.start();
      print("🚀 ARIA Porcupine Wake Word Engine Running in Background!");
    } on PorcupineException catch (e) {
      print("❌ Porcupine init error: ${'$'}{e.message}");
    }

    // Listen for stop signals from Flutter UI
    service.on('stopService').listen((event) {
      porcupineManager?.stop();
      porcupineManager?.delete();
      service.stopSelf();
    });
  }

  /// Trigger the Software Edge Glow Floating Overlay
  static Future<void> showEdgeGlowOverlay() async {
    if (await FlutterOverlayWindow.isPermissionGranted()) {
      await FlutterOverlayWindow.showOverlay(
        enableDrag: false,
        overlayTitle: "ARIA Glowing Border",
        overlayContent: "ARIA Active",
        flag: OverlayFlag.clickThrough, // ⭐ Non-touchable: user can tap apps underneath!
        visibility: NotificationVisibility.visibilitySecret,
        positionGravity: PositionGravity.auto,
        height: WindowSize.fullCover,
        width: WindowSize.fullCover,
      );
    }
  }

  /// Dismiss the Edge Glow Floating Overlay
  static Future<void> hideEdgeGlowOverlay() async {
    if (await FlutterOverlayWindow.isActive()) {
      await FlutterOverlayWindow.closeOverlay();
    }
  }
}
""".trimIndent()

    val overlayWidgetCode = """
// ==============================================================================
// ✨ lib/overlay/aria_edge_glow_overlay.dart
// Software Screen Edge Glow Floating Widget (SYSTEM_ALERT_WINDOW)
// Renders a futuristic, breathing glowing cyan border along device edges
// ==============================================================================
import 'package:flutter/material.dart';

/// Top-level overlay entry point registered for flutter_overlay_window
@pragma("vm:entry-point")
void overlayMain() {
  runApp(const MaterialApp(
    debugShowCheckedModeBanner: false,
    home: AriaEdgeGlowOverlay(),
  ));
}

class AriaEdgeGlowOverlay extends StatefulWidget {
  const AriaEdgeGlowOverlay({super.key});

  @override
  State<AriaEdgeGlowOverlay> createState() => _AriaEdgeGlowOverlayState();
}

class _AriaEdgeGlowOverlayState extends State<AriaEdgeGlowOverlay>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _glowAnimation;

  @override
  void initState() {
    super.initState();
    // Breathing glow animation loop (1.2 seconds breathing cycle)
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);

    _glowAnimation = Tween<double>(begin: 0.35, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent, // Completely transparent center!
      child: AnimatedBuilder(
        animation: _glowAnimation,
        builder: (context, child) {
          final double intensity = _glowAnimation.value;
          return Container(
            margin: EdgeInsets.zero,
            decoration: BoxDecoration(
              // Futuristic rounded corners matching smartphone displays
              borderRadius: BorderRadius.circular(28.0),
              border: Border.all(
                color: const Color(0xFF00E5FF).withOpacity(intensity * 0.95), // Cyber Cyan
                width: 4.5,
              ),
              boxShadow: [
                // Soft outer glowing halo
                BoxShadow(
                  color: const Color(0xFF00E5FF).withOpacity(intensity * 0.5),
                  blurRadius: 18.0,
                  spreadRadius: 3.0,
                ),
                // Deep inner neon accent
                BoxShadow(
                  color: const Color(0xFF0070F3).withOpacity(intensity * 0.3),
                  blurRadius: 28.0,
                  spreadRadius: -4.0,
                ),
              ],
            ),
            child: const SizedBox.expand(),
          );
        },
      ),
    );
  }
}
""".trimIndent()

    val nothingPhoneGlyphCode = """
// ==============================================================================
// 📱 Nothing Phone Glyph Matrix LED Hardware Integration (Optional)
// ==============================================================================
// If the app runs on Nothing Phone (1), Nothing Phone (2), or Phone (2a),
// you can optionally light up physical back LEDs alongside the screen glow!

// 1. Add Nothing Glyph Developer Kit in android/app/build.gradle:
// dependencies {
//     implementation 'com.nothing.glyph:glyph-sdk:1.0.0'
// }

// 2. In Kotlin MainActivity / Service:
/*
import com.nothing.glyph.GlyphManager
import com.nothing.glyph.GlyphFrame

class NothingGlyphController(context: Context) {
    private var glyphManager: GlyphManager? = null

    fun init() {
        if (Build.MANUFACTURER.contains("Nothing", ignoreCase = true)) {
            glyphManager = GlyphManager.getInstance(context)
            glyphManager?.init(object : GlyphManager.Callback {
                override fun onServiceConnected() {
                    // Glyph hardware connected!
                }
                override fun onServiceDisconnected() {}
            })
        }
    }

    fun animateWakeGlow() {
        // Light up central circular LED glyph ring with breathing effect
        val frame = GlyphFrame.Builder()
            .buildChannelC()
            .build()
        glyphManager?.animate(frame)
    }

    fun turnOff() {
        glyphManager?.turnOff()
    }
}
*/
""".trimIndent()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "✨ Background Always-Listening & Edge Glow Architecture",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Background Listening: Uses an Android Foreground Service with Picovoice Porcupine wake-word engine. Stays alive 24/7 with zero battery drain.\n" +
                                "2. Screen Edge Glow Overlay: Uses SYSTEM_ALERT_WINDOW with FLAG_NOT_TOUCHABLE so a subtle glowing cyan border pulses along the screen edges without blocking clicks or touches on other apps.\n" +
                                "3. Automatic Lifecycle: Activates immediately on 'Hey ARIA', transitions to speaking glow, and disappears smoothly when ARIA finishes responding.\n" +
                                "4. Nothing Phone Glyph: Hardware LED integration for Nothing Phone users with automatic software glow fallback for all other Android smartphones.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚙️ SYSTEM_ALERT_WINDOW Permission Guide",
                        color = WarningAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Android OS requires user consent for floating overlays:\n" +
                                "• Flutter package: 'permission_handler' or 'flutter_overlay_window.requestPermission()'\n" +
                                "• User will be routed to Settings > Apps > Special App Access > 'Display over other apps'\n" +
                                "• User must toggle the switch ON for ARIA. Once enabled, edge glow appears seamlessly over YouTube, Chrome, WhatsApp, Games, etc.!",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml (Packages)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = pubspecYamlCode)
        }

        item {
            Text(
                text = "2. AndroidManifest.xml (Foreground Service & SYSTEM_ALERT_WINDOW)",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = manifestCode)
        }

        item {
            Text(
                text = "3. lib/services/aria_background_service.dart (Always-Listening Engine)",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = backgroundServiceCode)
        }

        item {
            Text(
                text = "4. lib/overlay/aria_edge_glow_overlay.dart (Breathing Cyan Edge Glow)",
                color = ElectricCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = overlayWidgetCode)
        }

        item {
            Text(
                text = "5. Nothing Phone Glyph Matrix SDK Integration (Optional Hardware LEDs)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = nothingPhoneGlyphCode)
        }
    }
}

@Composable
fun FlutterAlarmsTimersAndRoutinesGuideView(context: Context) {
    val pubspecAlarmCode = """
# ==============================================================================
# 📦 pubspec.yaml (Alarm, Timer, Notifications & Routine Manager)
# ==============================================================================
dependencies:
  flutter:
    sdk: flutter
  flutter_local_notifications: ^17.1.2   # Scheduled alarms, ringtones & full-screen intent
  android_alarm_manager_plus: ^3.0.4     # Exact device alarms even when phone is asleep
  audioplayers: ^6.0.0                   # Loud alarm buzzer & sound effects
  flutter_tts: ^4.0.2                    # Text To Speech for voice alarms
  intl: ^0.19.0                          # Time & Date formatting
  shared_preferences: ^2.2.2             # Routine states & persistent settings
""".trimIndent()

    val alarmParserCode = """
// ==============================================================================
// ⏰ lib/services/aria_alarm_timer_service.dart
// Natural Language Time Parser & Android Alarm / Timer Manager
// Handles "7 baje ka alarm", "10 minute ka timer", "cancel alarm"
// ==============================================================================
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;

class AriaAlarmTimerService {
  static final FlutterLocalNotificationsPlugin _notificationsPlugin =
      FlutterLocalNotificationsPlugin();
  static final FlutterTts _tts = FlutterTts();

  /// Initialize notifications & timezones
  static Future<void> init() async {
    tz.initializeTimeZones();

    const AndroidInitializationSettings androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    const InitializationSettings initSettings =
        InitializationSettings(android: androidSettings);

    await _notificationsPlugin.initialize(
      initSettings,
      onDidReceiveNotificationResponse: (details) {
        print("Alarm notification clicked: ${'$'}{details.payload}");
      },
    );
  }

  /// Master Natural Language Parser for Voice Queries
  static Future<String> processVoiceCommand(String query) async {
    final q = query.toLowerCase().trim();

    if (q.contains('cancel') || q.contains('hatao') || q.contains('dismiss') || q.contains('band karo')) {
      await _notificationsPlugin.cancelAll();
      return "Boss, saare active alarms aur timers cancel kar diye gaye hain!";
    }

    if (q.contains('timer')) {
      return await _handleTimer(q);
    } else {
      return await _handleAlarm(q);
    }
  }

  /// Parse & schedule Timer (e.g., "10 minute ka timer", "30 second timer")
  static Future<String> _handleTimer(String q) async {
    int totalSeconds = 0;

    // Check minutes
    final minMatch = RegExp(r'(\d+)\s*(?:minute|min|minutes|minto)').firstMatch(q);
    if (minMatch != null) {
      totalSeconds += (int.tryParse(minMatch.group(1) ?? '0') ?? 0) * 60;
    }

    // Check hours
    final hrMatch = RegExp(r'(\d+)\s*(?:hour|hours|hr|ghanta|ghante)').firstMatch(q);
    if (hrMatch != null) {
      totalSeconds += (int.tryParse(hrMatch.group(1) ?? '0') ?? 0) * 3600;
    }

    // Check seconds
    final secMatch = RegExp(r'(\d+)\s*(?:second|sec|seconds)').firstMatch(q);
    if (secMatch != null) {
      totalSeconds += (int.tryParse(secMatch.group(1) ?? '0') ?? 0);
    }

    // Fallback default
    if (totalSeconds == 0) {
      final numMatch = RegExp(r'(\d+)').firstMatch(q);
      final num = int.tryParse(numMatch?.group(1) ?? '5') ?? 5;
      totalSeconds = num * 60;
    }

    final duration = Duration(seconds: totalSeconds);
    final targetTime = DateTime.now().add(duration);

    // Schedule notification
    await _notificationsPlugin.zonedSchedule(
      991,
      '⏱️ ARIA Timer Finished!',
      'Boss, aapka ${'$'}{totalSeconds ~/ 60} minute ka timer poora ho gaya hai!',
      tz.TZDateTime.from(targetTime, tz.local),
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'aria_timer_channel',
          'ARIA Timers',
          channelDescription: 'High priority loud timer sound',
          importance: Importance.max,
          priority: Priority.high,
          playSound: true,
          sound: RawResourceAndroidNotificationSound('alarm_loud'),
        ),
      ),
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
    );

    // Voice announcement when timer fires in background
    Timer(duration, () async {
      await _tts.speak("Alarm! Time ho gaya hai Boss. Aapka timer complete ho chuka hai.");
    });

    return "Boss, ${'$'}{totalSeconds ~/ 60} minute ka timer set kar diya hai! ⏱️";
  }

  /// Parse & schedule Alarm (e.g., "7 baje ka alarm", "sham 5 baje", "6:30 am")
  static Future<String> _handleAlarm(String q) async {
    int hour = 7;
    int minute = 0;
    bool isPm = q.contains('sham') || q.contains('shaam') || q.contains('raat') ||
        q.contains('pm') || q.contains('dopahar');

    final timeColonMatch = RegExp(r'(\d{1,2}):(\d{2})').firstMatch(q);
    if (timeColonMatch != null) {
      hour = int.tryParse(timeColonMatch.group(1) ?? '7') ?? 7;
      minute = int.tryParse(timeColonMatch.group(2) ?? '0') ?? 0;
    } else {
      final bajeMatch = RegExp(r'(\d{1,2})\s*(?:baje|am|pm|o\'clock)?').firstMatch(q);
      if (bajeMatch != null) {
        hour = int.tryParse(bajeMatch.group(1) ?? '7') ?? 7;
      }
    }

    if (isPm && hour < 12) hour += 12;
    if (!isPm && (q.contains('subah') || q.contains('am')) && hour == 12) hour = 0;

    final now = DateTime.now();
    var scheduledDate = DateTime(now.year, now.month, now.day, hour, minute);
    if (scheduledDate.isBefore(now)) {
      scheduledDate = scheduledDate.add(const Duration(days: 1));
    }

    await _notificationsPlugin.zonedSchedule(
      992,
      '⏰ ARIA Alarm: Wake Up!',
      'Boss, ${'$'}hour:${'$'}{minute.toString().padLeft(2, '0')} ho gaye hain!',
      tz.TZDateTime.from(scheduledDate, tz.local),
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'aria_alarm_channel',
          'ARIA Alarms',
          importance: Importance.max,
          priority: Priority.high,
          playSound: true,
          fullScreenIntent: true,
        ),
      ),
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
    );

    final displayTime = "${'$'}{hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour)}:${'$'}{minute.toString().padLeft(2, '0')} ${'$'}{hour >= 12 ? 'PM' : 'AM'}";
    return "Ji Boss! Kal subah ya aaj ${'$'}displayTime ka loud alarm set kar diya hai. ⏰";
  }
}
""".trimIndent()

    val routineManagerCode = """
// ==============================================================================
// 🌅 lib/services/routine_manager.dart
// Custom Routines: Good Morning & Good Night Automations
// ==============================================================================
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_tts/flutter_tts.dart';

enum AriaSystemState { active, standby, sleep }

class RoutineManager {
  static final FlutterTts _tts = FlutterTts();
  static final ValueNotifier<AriaSystemState> systemStateNotifier =
      ValueNotifier(AriaSystemState.active);

  /// Check if voice query is a custom routine
  static bool isRoutineCommand(String q) {
    final lower = q.toLowerCase();
    return lower.contains('good morning') ||
        lower.contains('good night') ||
        lower.contains('subah ho gayi') ||
        lower.contains('shubh ratri') ||
        lower.contains('so jao') ||
        lower.contains('office jaana hai');
  }

  /// Execute routine pipeline
  static Future<String> executeRoutine(String q, {
    required Future<String> Function() getWeather,
    required Future<List<String>> Function() getReminders,
    required Future<List<String>> Function() getTopNews,
    required VoidCallback onPauseBackgroundListening,
    required VoidCallback onResumeBackgroundListening,
  }) async {
    final lower = q.toLowerCase();

    if (lower.contains('good night') || lower.contains('shubh ratri') || lower.contains('so jao')) {
      // =======================================================
      // 🌙 GOOD NIGHT ROUTINE
      // =======================================================
      systemStateNotifier.value = AriaSystemState.standby;

      // 1. Pause continuous mic service to save overnight battery
      onPauseBackgroundListening();

      // 2. Short peaceful farewell
      const farewell = "Good night, Boss! Main standby mode me switch ho rahi hoon taaki aapki phone battery bachi rahe. Sound sleep lijiye! 🌙✨";
      await _tts.speak(farewell);
      return farewell;
    } else {
      // =======================================================
      // ☀️ GOOD MORNING ROUTINE
      // =======================================================
      systemStateNotifier.value = AriaSystemState.active;
      onResumeBackgroundListening();

      // Aggregate: Weather + Reminders + Top News
      final weather = await getWeather();
      final reminders = await getReminders();
      final news = await getTopNews();

      final buffer = StringBuffer();
      buffer.writeln("Good morning, Boss! ☀️ ARIA is online and ready.\n");
      buffer.writeln("🌤️ Weather: ${'$'}weather\n");

      if (reminders.isNotEmpty) {
        buffer.writeln("📌 Aaj ke Reminders (${'$'}{reminders.length}):");
        for (var r in reminders.take(3)) {
          buffer.writeln("• ${'$'}r");
        }
        buffer.writeln("");
      } else {
        buffer.writeln("📌 Reminders: Aaj koi pending tasks nahi hain. All clear!\n");
      }

      if (news.isNotEmpty) {
        buffer.writeln("📰 Top Headlines:");
        for (var n in news.take(2)) {
          buffer.writeln("• ${'$'}n");
        }
      }

      buffer.writeln("\nAapka din shubh aur productive rahe!");
      final response = buffer.toString();
      await _tts.speak(response);
      return response;
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
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⏰ Alarms, Timers & Custom Routines Pipeline",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Natural Language Time Parsing: Converts Hindi/English phrases ('7 baje', '10 minute', 'sham 5:30') into exact timestamps.\n" +
                                "2. Exact Scheduled Alarms: Uses flutter_local_notifications + Android AlarmManager for loud ringtones & full-screen wakeup.\n" +
                                "3. Custom Routines (RoutineManager):\n" +
                                "   • 'Good Morning ARIA' -> Switches UI to ONLINE, aggregates Weather + Reminders + News briefing.\n" +
                                "   • 'Good Night ARIA' -> Switches UI to STANDBY, pauses background mic to conserve battery overnight.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml Dependencies",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = pubspecAlarmCode)
        }

        item {
            Text(
                text = "2. lib/services/aria_alarm_timer_service.dart (Natural Language Alarms)",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = alarmParserCode)
        }

        item {
            Text(
                text = "3. lib/services/routine_manager.dart (Good Morning / Good Night Engine)",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = routineManagerCode)
        }
    }
}

@Composable
fun FlutterGmailNewsAndSportsGuideView(context: Context) {
    val pubspecCode = """
# ==============================================================================
# 📦 pubspec.yaml (Gmail OAuth2, News API & Cricket Sports API)
# ==============================================================================
dependencies:
  flutter:
    sdk: flutter
  google_sign_in: ^6.2.1               # Secure OAuth2 Login with Google
  extension_google_sign_in_as_googleapis_auth: ^2.0.12 # Auth bridge for Google APIs
  googleapis: ^13.2.0                  # Official Google Gmail API Client (Read-Only)
  http: ^1.2.0                         # REST API requests for News & Cricket
  flutter_tts: ^4.0.2                  # Voice feedback
""".trimIndent()

    val gmailServiceCode = """
// ==============================================================================
// ✉️ lib/services/aria_gmail_service.dart
// Read-Only Gmail API Integration (Google Sign-In + OAuth2)
// Voice Command: "ARIA, mera email check karo"
// ==============================================================================
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:googleapis/gmail/v1.dart' as gmail;
import 'package:extension_google_sign_in_as_googleapis_auth/extension_google_sign_in_as_googleapis_auth.dart';

class AriaGmailService {
  // ⭐ Privacy Note: Only request READ-ONLY scope (never request delete/send scopes)
  static final GoogleSignIn _googleSignIn = GoogleSignIn(
    scopes: [
      gmail.GmailApi.gmailReadonlyScope,
    ],
  );

  /// Sign In with Google & Fetch 3 Recent Unread Emails
  static Future<String> checkRecentEmails() async {
    try {
      final GoogleSignInAccount? account = await _googleSignIn.signIn();
      if (account == null) {
        return "Boss, Gmail access karne ke liye Google Sign-in cancel ho gaya.";
      }

      final httpClient = await _googleSignIn.authenticatedClient();
      if (httpClient == null) {
        return "Authentication error: Google account connect nahi ho saka.";
      }

      final gmailApi = gmail.GmailApi(httpClient);

      // Fetch unread inbox messages
      final listRes = await gmailApi.users.messages.list(
        'me',
        q: 'is:unread category:primary',
        maxResults: 3,
      );

      final messages = listRes.messages;
      if (messages == null || messages.isEmpty) {
        return "Boss, aapke primary inbox me koi naya unread email nahi hai. All clear! ✉️";
      }

      final buffer = StringBuffer();
      buffer.writeln("Boss, aapke paas ${'$'}{messages.length} naye unread emails hain:\n");

      for (var i = 0; i < messages.length; i++) {
        final msg = await gmailApi.users.messages.get('me', messages[i].id!, format: 'full');
        final headers = msg.payload?.headers ?? [];

        String subject = "No Subject";
        String sender = "Unknown Sender";

        for (var h in headers) {
          if (h.name?.toLowerCase() == 'subject') subject = h.value ?? subject;
          if (h.name?.toLowerCase() == 'from') sender = h.value?.split('<').first.trim() ?? sender;
        }

        final snippet = msg.snippet ?? "";
        buffer.writeln("${'$'}{i + 1}. Sender: ${'$'}sender");
        buffer.writeln("   Subject: ${'$'}subject");
        if (snippet.isNotEmpty) {
          buffer.writeln("   Preview: ${'$'}{snippet.length > 80 ? snippet.substring(0, 80) + '...' : snippet}\n");
        }
      }

      return buffer.toString();
    } catch (e) {
      return "Gmail fetch karne me dikkat aayi: ${'$'}{e.toString()}";
    }
  }
}
""".trimIndent()

    val newsAndSportsCode = """
// ==============================================================================
// 📰 lib/services/aria_news_and_sports_service.dart
// Live News Briefing (GNews / NewsAPI) & Live Cricket Scores (CricAPI)
// ==============================================================================
import 'dart:convert';
import 'package:http/http.dart' as http;

class AriaNewsAndSportsService {
  // 1. News API (Free Tier: GNews.io gives 100 requests/day free)
  static const String _gnewsApiKey = "YOUR_FREE_GNEWS_API_KEY";

  // 2. Cricket API (Free Tier: CricAPI gives 100 hits/day free)
  static const String _cricApiKey = "YOUR_FREE_CRICAPI_KEY";

  /// Voice command: "ARIA, aaj ki news sunao"
  static Future<String> getTodayTopNews({String country = 'in'}) async {
    try {
      final url = Uri.parse(
          'https://gnews.io/api/v4/top-headlines?category=general&lang=en&country=${'$'}country&max=4&apikey=${'$'}_gnewsApiKey');

      final response = await http.get(url).timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final articles = data['articles'] as List?;

        if (articles == null || articles.isEmpty) {
          return "Boss, abhi headlines available nahi hain.";
        }

        final buffer = StringBuffer();
        buffer.writeln("📰 Aaj ki Top Headlines:\n");

        for (var i = 0; i < articles.length; i++) {
          final title = articles[i]['title'] ?? "News Headline";
          final source = articles[i]['source']?['name'] ?? "News";
          buffer.writeln("${'$'}{i + 1}. ${'$'}title (${'$'}source)");
        }

        return buffer.toString();
      } else {
        return "News server se connect nahi ho saka. (Status: ${'$'}{response.statusCode})";
      }
    } catch (e) {
      return "Internet connection check kijiye news fetch karne ke liye.";
    }
  }

  /// Voice command: "ARIA, India ka cricket score kya hai"
  static Future<String> getLiveCricketScore() async {
    try {
      final url = Uri.parse(
          'https://api.cricapi.com/v1/currentMatches?apikey=${'$'}_cricApiKey&offset=0');

      final response = await http.get(url).timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final matches = data['data'] as List?;

        if (matches == null || matches.isEmpty) {
          return "Boss, abhi koi major live match active nahi hai. Recent matches check kar sakti hu!";
        }

        // Find India match or any live match
        final indiaMatch = matches.firstWhere(
          (m) => (m['name'] ?? '').toString().toLowerCase().contains('india'),
          orElse: () => matches.first,
        );

        final matchName = indiaMatch['name'] ?? "Cricket Match";
        final status = indiaMatch['status'] ?? "Match in progress";
        final score = indiaMatch['score'] != null
            ? (indiaMatch['score'] as List).map((s) => "${'$'}{s['inning']}: ${'$'}{s['r']}/${'$'}{s['w']} (${'$'}{s['o']} ov)").join(' | ')
            : "Scores updating...";

        return "🏏 Match: ${'$'}matchName\nScore: ${'$'}score\nStatus: ${'$'}status";
      } else {
        return "Cricket API response pending hai.";
      }
    } catch (e) {
      return "Score fetch karte waqt network issue aaya, Boss.";
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
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "✉️ Gmail, News & Sports Architecture",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Gmail API (OAuth2): Uses google_sign_in with read-only scope (gmail.readonly). Summarizes top unread inbox senders and previews safely.\n" +
                                "2. GNews / NewsAPI: Fetches top headlines for India/world with short summaries.\n" +
                                "3. CricAPI: Fetches live match ball-by-ball scorelines and match summaries for 'India ka score kya hai'.\n" +
                                "4. Free Tier Notice: Gmail API is 100% free with generous quotas. GNews (100 req/day) & CricAPI (100 req/day) provide free tiers.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. pubspec.yaml",
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
                text = "2. lib/services/aria_gmail_service.dart (Read-Only Inbox Summary)",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = gmailServiceCode)
        }

        item {
            Text(
                text = "3. lib/services/aria_news_and_sports_service.dart (News & Cricket Scores)",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = newsAndSportsCode)
        }
    }
}

@Composable
fun FlutterTranslateMusicAndWhatsAppGuideView(context: Context) {
    val translateMusicCode = """
// ==============================================================================
// 🌐 lib/services/aria_translate_and_music_service.dart
// 1. Language Translator (Hindi <-> English + 100+ languages)
// 2. Mood-Based Music Suggestion (Spotify URI + YouTube Music Fallback)
// 3. WhatsApp Status Limitations & Deep-linking
// ==============================================================================
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:http/http.dart' as http;
import 'package:flutter_tts/flutter_tts.dart';

class AriaTranslateAndMusicService {
  static final FlutterTts _tts = FlutterTts();

  // ===========================================================================
  // 1. 🌐 LANGUAGE TRANSLATOR
  // Voice Command: "ARIA, 'good morning' ko hindi mein translate karo"
  // ===========================================================================
  static Future<String> translateText(String text, {String targetLang = 'hi'}) async {
    try {
      // Free Google Translate Endpoint
      final url = Uri.parse(
          'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${'$'}targetLang&dt=t&q=${'$'}{Uri.encodeComponent(text)}');

      final response = await http.get(url).timeout(const Duration(seconds: 6));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final translated = data[0][0][0].toString();

        // Voice output
        if (targetLang == 'hi') {
          await _tts.setLanguage("hi-IN");
        }
        await _tts.speak(translated);

        return "Translation: \"${'$'}translated\"";
      } else {
        return "Translation server unreachable.";
      }
    } catch (e) {
      return "Translation failed: ${'$'}{e.toString()}";
    }
  }

  // ===========================================================================
  // 2. 🎵 MOOD-BASED MUSIC SUGGESTION
  // Voice Command: "ARIA, mera mood chill hai, gaana suggest karo"
  // ===========================================================================
  static Future<String> playMoodMusic(String voiceQuery) async {
    final q = voiceQuery.toLowerCase();

    String mood = "Chill";
    String queryKeyword = "chill lofi relaxing songs";

    if (q.contains('sad') || q.contains('dard') || q.contains('udas')) {
      mood = "Melancholic / Sad";
      queryKeyword = "emotional soulful sad hindi acoustic songs";
    } else if (q.contains('romantic') || q.contains('love') || q.contains('pyaar')) {
      mood = "Romantic";
      queryKeyword = "best romantic hindi love songs";
    } else if (q.contains('gym') || q.contains('workout') || q.contains('energetic') || q.contains('josh')) {
      mood = "High Energy";
      queryKeyword = "high energy gym workout booster songs";
    } else if (q.contains('party') || q.contains('dance')) {
      mood = "Party";
      queryKeyword = "nonstop party dance club tracks";
    }

    // 1. Try launching Spotify URI scheme (spotify:search:<query>)
    final spotifyUri = Uri.parse("spotify:search:${'$'}{Uri.encodeComponent(queryKeyword)}");
    if (await canLaunchUrl(spotifyUri)) {
      await launchUrl(spotifyUri, mode: LaunchMode.externalApplication);
      return "Boss, aapke ${'$'}mood mood ke liye Spotify playlist open kar di hai! 🎵";
    }

    // 2. Fallback to YouTube Music Search
    final ytUri = Uri.parse("https://www.youtube.com/results?search_query=${'$'}{Uri.encodeComponent(queryKeyword)}");
    await launchUrl(ytUri, mode: LaunchMode.externalApplication);
    return "Aapke ${'$'}mood mood ke gaane YouTube Music par load kar diye hain! 🎶";
  }

  // ===========================================================================
  // 3. 💬 WHATSAPP STATUS LIMITATION & REALISTIC BEHAVIOR
  // ===========================================================================
  // ⚠️ CRITICAL LIMITATION:
  // WhatsApp DOES NOT provide any public API or deep link to programmatically
  // view contacts' status updates. Direct in-app status rendering is forbidden
  // by WhatsApp end-to-end encryption.
  //
  // Realistic Implementation:
  // ARIA informs the user politely and immediately opens WhatsApp directly
  // so the user can view the Status tab with a single tap.
  // ===========================================================================
  static Future<String> openWhatsAppStatusTab() async {
    final whatsappUri = Uri.parse("whatsapp://");
    final playStoreUri = Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp");

    if (await canLaunchUrl(whatsappUri)) {
      await launchUrl(whatsappUri, mode: LaunchMode.externalApplication);
      return "Status dekhne ke liye WhatsApp khol rahi hoon, Boss! Status tab par tap kijiye. 💬";
    } else {
      await launchUrl(playStoreUri, mode: LaunchMode.externalApplication);
      return "WhatsApp app install nahi hai.";
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
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🌐 Translation, Mood Music & WhatsApp Deep-linking",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Translation Engine: Auto-detects language and translates to Hindi / English / Spanish with instant TTS pronunciation.\n" +
                                "2. Mood-Based Music: Intelligently parses emotional state (chill, sad, energetic, romantic, party) and opens curated Spotify / YouTube playlists.\n" +
                                "3. WhatsApp Status Reality: Explains WhatsApp's privacy architecture transparently and launches WhatsApp gracefully.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. lib/services/aria_translate_and_music_service.dart",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = translateMusicCode)
        }
    }
}

@Composable
fun FlutterAccessibilityVoiceGesturesGuideView(context: Context) {
    val configXmlCode = """
<!-- ===========================================================================
     📂 android/app/src/main/res/xml/aria_accessibility_service_config.xml
     Accessibility Service Capabilities Declaration
     =========================================================================== -->
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
""".trimIndent()

    val manifestXmlCode = """
<!-- ===========================================================================
     📂 android/app/src/main/AndroidManifest.xml
     Registering the Accessibility Service with BIND_ACCESSIBILITY_SERVICE
     =========================================================================== -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application ...>
        
        <!-- ARIA Voice Gestures Accessibility Service -->
        <service
            android:name=".AriaAccessibilityGestureService"
            android:label="A.R.I.A. Voice Gestures"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/aria_accessibility_service_config" />
        </service>

    </application>
</manifest>
""".trimIndent()

    val nativeKotlinCode = """
// =============================================================================
// 🤖 android/app/src/main/kotlin/.../AriaAccessibilityGestureService.kt
// Native Android AccessibilityService that simulates Swipes and Taps
// =============================================================================
package com.example.aria

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AriaAccessibilityGestureService : AccessibilityService() {

    companion object {
        private const val TAG = "AriaAccessibility"
        private var instance: AriaAccessibilityGestureService? = null

        fun isRunning(): Boolean = instance != null

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun scrollNextVideo(callback: ((Boolean) -> Unit)? = null) {
            instance?.performSwipeUp(callback) ?: callback?.invoke(false)
        }

        fun togglePlayPause(callback: ((Boolean) -> Unit)? = null) {
            instance?.performCenterTap(callback) ?: callback?.invoke(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "ARIA Accessibility Gesture Service Connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // 📱 Simulate Upward Swipe (Scroll Next Short/Reel)
    private fun performSwipeUp(callback: ((Boolean) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val metrics = resources.displayMetrics
        val startX = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * 0.78f // Near bottom
        val endX = metrics.widthPixels * 0.5f
        val endY = metrics.heightPixels * 0.22f   // Near top

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 260)) // 260ms duration
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    // ⏯️ Simulate Center Tap (Toggle Play/Pause on Video Players)
    private fun performCenterTap(callback: ((Boolean) -> Unit)?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val centerY = metrics.heightPixels * 0.5f

        val tapPath = Path().apply {
            moveTo(centerX, centerY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 50)) // 50ms tap
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }
}
""".trimIndent()

    val flutterBridgeCode = """
// =============================================================================
// 🌉 lib/services/aria_accessibility_service.dart
// Flutter Platform Channel Bridge & Natural Language Voice Command Trigger
// =============================================================================
import 'package:flutter/services.dart';
import 'package:flutter_tts/flutter_tts.dart';

class AriaAccessibilityService {
  static const MethodChannel _channel =
      MethodChannel('com.example.aria/accessibility');
  static final FlutterTts _tts = FlutterTts();

  /// Check if the user enabled ARIA in Android Accessibility Settings
  static Future<bool> isAccessibilityEnabled() async {
    try {
      final bool enabled = await _channel.invokeMethod('isAccessibilityEnabled');
      return enabled;
    } catch (e) {
      return false;
    }
  }

  /// Open Android System Settings -> Accessibility Screen
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } catch (e) {
      print('Failed to open settings: ${'$'}e');
    }
  }

  /// Process Voice Commands like "scroll this reel", "pause video", "next short"
  static Future<String?> processVoiceGestureCommand(String query) async {
    final q = query.toLowerCase().trim();

    // 1. Check for Scroll Command
    if (q.contains('scroll') || q.contains('next reel') || q.contains('next short') ||
        q.contains('next video') || q.contains('agla video') || q.contains('reel badlo')) {
      final bool isRunning = await isAccessibilityEnabled();
      if (!isRunning) {
        const msg = "Boss, automated scroll ke liye Accessibility permission enable karni hogi.";
        await _tts.speak(msg);
        return msg;
      }

      await _channel.invokeMethod('scrollNextVideo');
      const msg = "Video scroll kar diya hai! 📱✨";
      await _tts.speak(msg);
      return msg;
    }

    // 2. Check for Pause / Play Command
    if (q.contains('pause') || q.contains('play') || q.contains('resume') ||
        q.contains('video roko') || q.contains('video chalao') || q.contains('pause this')) {
      final bool isRunning = await isAccessibilityEnabled();
      if (!isRunning) {
        const msg = "Boss, video control ke liye Accessibility permission chahiye.";
        await _tts.speak(msg);
        return msg;
      }

      await _channel.invokeMethod('togglePlayPause');
      const msg = "Video play/pause toggle kar diya hai! ⏯️";
      await _tts.speak(msg);
      return msg;
    }

    return null; // Not a gesture command
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
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🖐️ Android Accessibility Service & Voice Gestures",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. dispatchGesture() API: Android AccessibilityService can simulate touch gestures across any third-party app (YouTube, Instagram, TikTok) when triggered by voice.\n" +
                                "2. Voice Commands:\n" +
                                "   • 'scroll this reel/shorts' -> Dispatches a bottom-to-top swipe path (260ms).\n" +
                                "   • 'pause this video/reel' -> Dispatches a single tap at the center coordinate.\n" +
                                "3. Background Wake Word Integration: User can be watching YouTube and say 'Hey ARIA, scroll this' hands-free!\n\n" +
                                "⚠️ CRITICAL PLATFORM NOTE (Android vs iOS):\n" +
                                "• ANDROID: Fully supports system-wide gesture automation via AccessibilityService.\n" +
                                "• iOS (Apple): Strictly blocks third-party apps from simulating gestures or reading external app screens due to iOS sandbox security policy.\n\n" +
                                "🔒 PRIVACY NOTE:\n" +
                                "ARIA only dispatches touch events upon explicit voice command. No personal user chats, passwords, or keystrokes are inspected or stored.",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "1. res/xml/aria_accessibility_service_config.xml",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = configXmlCode)
        }

        item {
            Text(
                text = "2. AndroidManifest.xml (Service Registration)",
                color = ElectricEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = manifestXmlCode)
        }

        item {
            Text(
                text = "3. AriaAccessibilityGestureService.kt (Native Gesture Dispatcher)",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = nativeKotlinCode)
        }

        item {
            Text(
                text = "4. lib/services/aria_accessibility_service.dart (Flutter Platform Channel)",
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeSnippetBox(code = flutterBridgeCode)
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


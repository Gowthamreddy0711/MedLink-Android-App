import { GoogleGenAI } from "@google/genai";

const API_KEY = process.env.GEMINI_API_KEY;

export async function chatWithAI(message: string, history: { role: string, parts: { text: string }[] }[]) {
  if (!API_KEY) {
    throw new Error('GEMINI_API_KEY is not defined');
  }

  const ai = new GoogleGenAI({ apiKey: API_KEY });
  const response = await ai.models.generateContent({ 
    model: "gemini-3-flash-preview",
    contents: [...history, { role: 'user', parts: [{ text: message }] }],
    config: {
      systemInstruction: "You are MedLink AI, a healthcare assistant. You help patients understand symptoms at a basic level and suggest doctor specialties. You MUST NOT provide any medical diagnosis or specific treatment plans. Always include a disclaimer that you are an AI and the user should consult a real doctor. Be polite, professional, and supportive.",
    }
  });

  return response.text || "I'm sorry, I couldn't generate a response.";
}

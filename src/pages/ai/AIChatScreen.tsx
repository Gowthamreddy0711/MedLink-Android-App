import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Send, ArrowLeft, Bot, User, Loader2, Info, Sparkles, AlertTriangle, ShieldCheck, Brain, Zap } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../constants';
import { chatWithAI } from '../../services/geminiService';
import { cn } from '../../lib/utils';

interface Message {
  id: string;
  role: 'user' | 'model';
  text: string;
  timestamp: Date;
  type?: 'diagnosis' | 'advice' | 'alert';
}

export default function AIChatScreen() {
  const navigate = useNavigate();
  const [messages, setMessages] = useState<Message[]>([
    { 
      id: '1', 
      role: 'model', 
      text: 'Hello! I am your MedLink Assistant. I can help analyze your symptoms and guide you to the right specialist. How are you feeling today?', 
      timestamp: new Date() 
    }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isTyping]);

  const handleSend = async (customInput?: string) => {
    const textToSend = customInput || input;
    if (!textToSend.trim() || isTyping) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      text: textToSend,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsTyping(true);

    try {
      const history = messages.map(m => ({
        role: m.role,
        parts: [{ text: m.text }]
      }));
      
      const response = await chatWithAI(textToSend, history);
      
      setMessages(prev => [...prev, {
        id: (Date.now() + 1).toString(),
        role: 'model',
        text: response,
        timestamp: new Date(),
        type: response.toLowerCase().includes('recommend') || response.toLowerCase().includes('specialist') ? 'diagnosis' : undefined
      }]);
    } catch (error) {
      setMessages(prev => [...prev, {
        id: (Date.now() + 1).toString(),
        role: 'model',
        text: 'Sorry, I encountered an error. Please try again later.',
        timestamp: new Date()
      }]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div className="flex flex-col min-h-screen overflow-x-hidden bg-slate-50">
      {/* Header */}
      <header className="sticky top-0 z-20 flex items-center justify-between border-b border-slate-100 bg-white p-4 sm:p-6">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(-1)} className="p-2 -ml-2 text-slate-600 bg-slate-50 rounded-xl">
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h1 className="text-xl font-black text-blue-900 tracking-tight flex items-center gap-2 leading-none">
              Smart Assistant <Sparkles className="w-5 h-5 text-blue-500 fill-blue-500" />
            </h1>
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mt-1">Medical Intelligence v2.0</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
           <div className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse" />
           <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest leading-none">Live</span>
        </div>
      </header>

      {/* Triage Banner */}
      <div className="relative z-10 flex items-center justify-between overflow-hidden bg-blue-600 px-4 py-3 text-white sm:px-6">
         <div className="absolute top-0 right-0 w-24 h-full bg-white/10 -skew-x-12 transform" />
         <div className="flex items-center gap-2 relative z-10">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <span className="text-[10px] font-bold uppercase tracking-widest leading-none">Not for emergencies</span>
         </div>
         <button className="text-[10px] font-black underline uppercase tracking-tight relative z-10">Privacy Policy</button>
      </div>

      {/* Messages */}
      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 overflow-y-auto p-4 sm:gap-6 sm:p-6">
        <AnimatePresence>
          {messages.map((m) => (
            <motion.div 
              key={m.id}
              initial={{ opacity: 0, y: 10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              className={cn(
                "max-w-[85%] p-5 rounded-[2rem]",
                m.role === 'user' 
                  ? "bg-blue-600 text-white self-end rounded-tr-none shadow-lg shadow-blue-100" 
                  : "bg-white border border-slate-100 text-blue-950 self-start rounded-tl-none shadow-sm"
              )}
            >
              {m.type === 'diagnosis' && m.role === 'model' && (
                <div className="flex items-center gap-2 mb-3 pb-3 border-b border-slate-50">
                   <div className="p-2 bg-blue-50 text-blue-600 rounded-xl">
                      <Brain className="w-4 h-4" />
                   </div>
                   <span className="text-[10px] font-black text-blue-900 uppercase tracking-widest">AI Analysis</span>
                </div>
              )}
              
              <div className="text-sm font-medium leading-relaxed whitespace-pre-wrap">
                {m.text}
              </div>

              {m.type === 'diagnosis' && m.role === 'model' && (
                <button 
                  onClick={() => navigate(ROUTES.PATIENT_SEARCH)}
                  className="mt-4 w-full bg-blue-900 text-white font-bold py-3 rounded-2xl text-[10px] uppercase tracking-widest flex items-center justify-center gap-2 active:scale-95 transition-transform"
                >
                   Book Specialist Consultation <Zap className="w-3 h-3 text-amber-400 fill-amber-400" />
                </button>
              )}

              <div className={cn(
                "mt-3 text-[9px] font-bold uppercase tracking-tight opacity-40",
                m.role === 'user' ? "text-right" : "text-left"
              )}>
                {m.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
        {isTyping && (
          <div className="bg-white border border-slate-100 p-5 rounded-[2rem] rounded-tl-none self-start flex items-center gap-2 shadow-sm">
            <span className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce [animation-delay:-0.3s]" />
            <span className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce [animation-delay:-0.15s]" />
            <span className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce" />
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="sticky bottom-0 z-20 border-t border-slate-100 bg-white p-4 pb-20 lg:pb-6 sm:p-6">
        {/* Suggestion Chips */}
        <div className="flex gap-2 overflow-x-auto pb-4 scrollbar-hide">
           {[
             { label: '🤒 Cold & Flu', text: 'I have a sore throat and slight fever' },
             { label: '🧠 Mental Health', text: 'I feel stressed and anxious lately' },
             { label: '💓 Heart Health', text: 'Tell me about heart disease prevention' }
           ].map((chip) => (
             <button 
               key={chip.label}
               onClick={() => handleSend(chip.text)}
               className="whitespace-nowrap px-4 py-2 bg-slate-50 border border-slate-100 rounded-full text-[10px] font-bold text-slate-500 hover:bg-blue-50 hover:text-blue-600 hover:border-blue-200 transition-all active:scale-95"
             >
                {chip.label}
             </button>
           ))}
        </div>

        <div className="bg-slate-50 rounded-[2rem] p-2 flex items-center border border-slate-100 shadow-inner group focus-within:border-blue-300 transition-all">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Describe your symptoms exactly..."
            className="flex-1 bg-transparent border-none focus:ring-0 px-4 py-3 placeholder:text-slate-400 font-medium text-sm text-blue-950"
          />
          <button
            onClick={() => handleSend()}
            disabled={isTyping || !input.trim()}
            className="p-4 bg-blue-600 text-white rounded-[1.5rem] shadow-lg shadow-blue-200 disabled:bg-slate-300 disabled:shadow-none active:scale-95 transition-all"
          >
            <Send className="w-5 h-5" />
          </button>
        </div>
        
        <div className="mt-4 flex items-center justify-center gap-2">
           <ShieldCheck className="w-3 h-3 text-emerald-500" />
           <span className="text-[10px] font-black text-slate-300 uppercase tracking-widest leading-none">HIPAA Compliant & End-to-End Encrypted</span>
        </div>
      </div>
    </div>
  );
}

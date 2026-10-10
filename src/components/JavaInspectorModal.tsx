import React, { useState } from 'react';
import {
  X, Code2, Copy, Check, FileCode, CheckCircle2, Award, Database, Layers, Globe
} from 'lucide-react';
import { JAVA_COMPONENTS, JavaComponentItem } from '../data/javaArchitecture';

interface JavaInspectorModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const JavaInspectorModal: React.FC<JavaInspectorModalProps> = ({ isOpen, onClose }) => {
  const [selectedComponent, setSelectedComponent] = useState<JavaComponentItem>(JAVA_COMPONENTS[0]);
  const [copied, setCopied] = useState(false);
  const [activeCategory, setActiveCategory] = useState<string>('ALL');

  if (!isOpen) return null;

  const categories = [
    'ALL',
    'GUVI Rubric Overview',
    'Problem Understanding & Solution Design (8 Marks)',
    'Core Java Concepts (10 Marks)',
    'Database Integration (JDBC) (8 Marks)',
    'Servlets & Web Integration (7 Marks)',
  ];

  const filteredComponents = JAVA_COMPONENTS.filter(c => {
    if (activeCategory === 'ALL') return true;
    return c.category === activeCategory;
  });

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const rubricPillCards = [
    {
      category: 'Problem Understanding & Solution Design (8 Marks)',
      label: '1. Problem & Architecture',
      marks: '8 / 8 Marks',
      icon: Layers,
      color: 'border-blue-500/40 bg-blue-500/10 text-blue-400',
    },
    {
      category: 'Core Java Concepts (10 Marks)',
      label: '2. Core Java & OOP',
      marks: '10 / 10 Marks',
      icon: Award,
      color: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400',
    },
    {
      category: 'Database Integration (JDBC) (8 Marks)',
      label: '3. Database & JDBC',
      marks: '8 / 8 Marks',
      icon: Database,
      color: 'border-amber-500/40 bg-amber-500/10 text-amber-400',
    },
    {
      category: 'Servlets & Web Integration (7 Marks)',
      label: '4. Servlets & Web',
      marks: '7 / 7 Marks',
      icon: Globe,
      color: 'border-purple-500/40 bg-purple-500/10 text-purple-400',
    },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-6xl h-[88vh] flex flex-col overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/90">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-purple-500/20 border border-purple-500/30 text-purple-400 flex items-center justify-center shrink-0">
              <Code2 className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="font-bold text-white text-base">Java Web Projects Marking Rubric &amp; Architecture (33 Marks)</h3>
                <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center space-x-1">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Full 33/33 Compliance</span>
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Evaluator Blueprint: Indian Stock Trading Platform (NSE/BSE), Java Servlets, JDBC, DAO Pattern, and OOP Architecture.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 4-Criteria Interactive Scorecard Strip */}
        <div className="bg-slate-950/60 border-b border-slate-800 px-4 py-2.5 grid grid-cols-2 sm:grid-cols-4 gap-2">
          {rubricPillCards.map((pill) => {
            const IconComponent = pill.icon;
            const isSelected = activeCategory === pill.category;
            return (
              <button
                key={pill.category}
                onClick={() => {
                  setActiveCategory(pill.category);
                  const firstMatch = JAVA_COMPONENTS.find(c => c.category === pill.category);
                  if (firstMatch) setSelectedComponent(firstMatch);
                }}
                className={`p-2 rounded-lg border text-left transition flex items-center space-x-2.5 ${
                  isSelected
                    ? `${pill.color} ring-1 ring-white/20 font-semibold`
                    : 'border-slate-800 bg-slate-900/60 hover:bg-slate-850 text-slate-400 hover:text-slate-200'
                }`}
              >
                <IconComponent className="w-4 h-4 shrink-0" />
                <div className="truncate">
                  <div className="text-[11px] font-medium leading-tight truncate">{pill.label}</div>
                  <div className="text-xs font-bold text-white mt-0.5">{pill.marks}</div>
                </div>
              </button>
            );
          })}
        </div>

        {/* Body Split */}
        <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
          {/* Component List */}
          <div className="w-full md:w-84 border-r border-slate-800 bg-slate-950/50 flex flex-col overflow-hidden">
            <div className="p-2.5 border-b border-slate-800/80 overflow-x-auto">
              <div className="flex space-x-1">
                {categories.map((cat) => (
                  <button
                    key={cat}
                    onClick={() => setActiveCategory(cat)}
                    className={`px-2 py-1 rounded text-[10px] whitespace-nowrap font-medium transition ${
                      activeCategory === cat
                        ? 'bg-purple-600 text-white'
                        : 'text-slate-400 hover:text-white hover:bg-slate-850'
                    }`}
                  >
                    {cat === 'Problem Understanding & Solution Design (8 Marks)' ? '1. Problem Design (8M)'
                      : cat === 'Core Java Concepts (10 Marks)' ? '2. Core Java (10M)'
                      : cat === 'Database Integration (JDBC) (8 Marks)' ? '3. JDBC DB (8M)'
                      : cat === 'Servlets & Web Integration (7 Marks)' ? '4. Servlets Web (7M)'
                      : cat}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
              {filteredComponents.map((comp) => (
                <button
                  key={comp.id}
                  onClick={() => setSelectedComponent(comp)}
                  className={`w-full p-2.5 rounded-lg text-left text-xs transition flex flex-col space-y-1 ${
                    selectedComponent.id === comp.id
                      ? 'bg-purple-600/20 text-purple-300 border border-purple-500/30 font-semibold'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900 border border-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-1.5 truncate">
                      <FileCode className="w-3.5 h-3.5 shrink-0 text-purple-400" />
                      <span className="truncate text-xs text-white font-medium">{comp.name}</span>
                    </div>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-300 shrink-0 ml-1">
                      {comp.marks}
                    </span>
                  </div>
                  <span className="text-[10px] text-slate-400 line-clamp-2">{comp.description}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Code Viewer Panel */}
          <div className="flex-1 flex flex-col bg-slate-950 overflow-hidden">
            <div className="p-3 border-b border-slate-800 bg-slate-900/40 flex items-center justify-between text-xs">
              <div className="flex items-center space-x-2">
                <span className="font-mono text-emerald-400 font-bold">{selectedComponent.path}</span>
                <span className="text-slate-500">&bull; {selectedComponent.marks}</span>
              </div>
              <button
                onClick={() => handleCopy(selectedComponent.codeSnippet)}
                className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-750 text-slate-300 text-xs flex items-center space-x-1.5 transition"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'Copied' : 'Copy Code'}</span>
              </button>
            </div>

            <div className="flex-1 p-4 overflow-auto">
              <pre className="font-mono text-xs text-slate-200 leading-relaxed whitespace-pre select-text">
                {selectedComponent.codeSnippet}
              </pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

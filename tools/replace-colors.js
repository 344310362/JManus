#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { promisify } = require('util');

const readdir = promisify(fs.readdir);
const readFile = promisify(fs.readFile);
const writeFile = promisify(fs.writeFile);

// Function to determine the appropriate text variable based on alpha value
function getTextVariableForAlpha(alpha) {
  const value = parseFloat(alpha);
  if (value >= 0.9) {
    return '--text-primary';
  } else if (value >= 0.6) {
    return '--text-secondary';
  } else {
    return '--text-tertiary';
  }
}

// Define replacements for specific color patterns
const COLOR_REPLACEMENTS = [
  // Replace '#764ba2' with '--primer-color' in all contexts
  {
    pattern: /#764ba2/g,
    replacement: 'var(--primer-color)'
  },
  // Replace 'white' with '--text-primary' for color properties
  {
    pattern: /\bcolor:\s*white\b/g,
    replacement: 'color: var(--text-primary)'
  },
  {
    pattern: /\bcolor:\s*#ffffff\b/g,
    replacement: 'color: var(--text-primary)'
  },
  // Handle specific border pattern
  {
    pattern: /border:\s*1px\s+solid\s+rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.1\s*\)/g,
    replacement: 'border: 1px solid var(--border-primary)'
  },
  // Handle rgba(255, 255, 255, x) for color properties with transparency-based variable selection
  {
    pattern: /color:\s*rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*([0-9.]+)\s*\)/g,
    replacement: (match, alpha) => {
      const variable = getTextVariableForAlpha(alpha);
      return `color: var(${variable})`;
    }
  },
  // Handle rgba(255, 255, 255, x) for background properties with transparency-based variable selection
  {
    pattern: /background:\s*rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*([0-9.]+)\s*\)/g,
    replacement: (match, alpha) => {
      // For backgrounds, we'll use bg variables with RGB values
      return `background: rgba(var(--bg-primary-rgb), ${alpha})`;
    }
  },
  // Fix incorrect variable usage
  {
    pattern: /color:\s*var\(--bg-input,\s*#ffffff\)/g,
    replacement: 'color: var(--text-primary, #ffffff)'
  },
  // Replace other specific patterns
  {
    pattern: /background:\s*#0a0a0a/g,
    replacement: 'background: var(--bg-primary)'
  },
  {
    pattern: /color:\s*#000000/g,
    replacement: 'color: var(--text-primary)'
  }
];

// Function to get all Vue files in a directory recursively
async function getVueFiles(dir) {
  let results = [];
  const list = await readdir(dir);

  for (const file of list) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);

    if (stat && stat.isDirectory()) {
      results = [...results, ...(await getVueFiles(filePath))];
    } else if (path.extname(file) === '.vue') {
      results.push(filePath);
    }
  }

  return results;
}

// Function to replace hardcoded colors with theme variables
function replaceColors(content) {
  let updatedContent = content;

  // Apply each replacement pattern
  COLOR_REPLACEMENTS.forEach(({ pattern, replacement }) => {
    if (typeof replacement === 'string') {
      updatedContent = updatedContent.replace(pattern, replacement);
    } else {
      // Handle function replacements
      updatedContent = updatedContent.replace(pattern, replacement);
    }
  });

  return updatedContent;
}

// Main function
async function main() {
  const args = process.argv.slice(2);
  // Default to current directory if no argument provided
  const viewsDir = args[0] || '.';

  console.log(`Searching for Vue files in: ${viewsDir}`);

  try {
    // Get all Vue files
    const vueFiles = await getVueFiles(viewsDir);
    console.log(`Found ${vueFiles.length} Vue files`);

    // Process each Vue file
    for (const file of vueFiles) {
      const content = await readFile(file, 'utf8');
      const updatedContent = replaceColors(content);

      if (content !== updatedContent) {
        await writeFile(file, updatedContent, 'utf8');
        console.log(`Updated: ${file}`);
      }
    }

    console.log('Color replacement completed!');
  } catch (err) {
    console.error('Error:', err);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

module.exports = {
  getVueFiles,
  replaceColors,
  getTextVariableForAlpha
};
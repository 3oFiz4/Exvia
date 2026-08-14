# Exvia

<img width="236" height="236" alt="logo" src="https://github.com/user-attachments/assets/049d1e07-5866-4c51-bcb8-d0a83479a0b1" />

_Exvia_, originates from Latin, _ex_ means Out, and _via_ means track, where it signify the act of tracking and monitoring every out (expenses) of your finance.

Minimal Android expense editor backed directly by JSON files in a GitHub repository, made especially for Programmers.

The app uses GitHub's REST Contents API. A write replaces the selected JSON file using its latest SHA, and GitHub creates the commit directly on `main`. No Git executable or local `.git` directory is stored on the phone.

This is intentional, because this app are made to respect the user's privacy. Meaning, we will not own your expenses, we will not monitor your income, our capability is therefore to provide an application where user are given maximum responsibility and freedom, to manage and to monitor their track. But at the cost, where user must create their own account, to control their own database. Additionally, an abundant knowledge of SQLite and JS is even much better.

# Showcase

https://github.com/user-attachments/assets/1a5c1559-9435-4bd3-8ea8-ae7c5da98057

<details>
  <summary>File structure</summary>
  <img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/163ce797-d0a1-4404-9b9e-ee7dbee643c0" />
</details>

# Installation

1. Create your own GitHub repository, and ensure it is private, and your GitHub PAT is initialized
2. Within the GitHub repository, initialize a folder within, it can be anything, I personally created `Financial/`
3. Within the folder, create your first expenses `.json`. It track any .json file under `Financial/`, I personally name it this way `@MMMM_expenses.json`
4. For the template, copy and paste this into the expenses file:

```
[
  {
    "date": "Test",
    "price": "999",
    "code": "Food",
    "description": "coffee"
  }
]
```

5. Ensure every changes it commited, now git clone this repository on your Android Studio `git clone https://github.com/3oFiz4/Exvia`
6. Install the apk
7. When you open the app for the first time, you will be instructed to input everything to the config (autosaves) it will require you to input your GitHub PAT, copy your GitHub PAT and paste it there. This is the "password" or "token" to access your repository, because it is private.
8. Enjoy

# Features

1. CRUD (Create-Read-Update-Delete) Operation for Expenses, Income, and for every Table.
2. Built-in Filtering via SQLite syntax (e.g., SELECT \* WHERE ...)
3. No managed databases, the app is not controlled by the author. You create your own, you manage it.
4. Automatic and smart synchronization between application with database
5. Configurable color for table's value
6. Extensive configuration, this includes GitHub as Database, Color, Scheme & Display, Custom metric, and Custom plot
7. Built-in Tutorial for first-time installation
8. Extensive statistical and financial metric, you can add your own if you want to.
9. Multi-file access and synchronization
10. Distinguishable logo and name
11. Beautiful font family
12. Explanation for each input and text, hold for 3 seconds to show tooltip
13. Developer Options (click 3x to the Title "Exvia" on Table to toggle)
14. Extended customization of plot, uses D3.JS (for complex plot) and ObservablePlot (for simpler plot), you can make your own! (If you are not familiar with programming, please use AI)
15. Report button, so you do not have create Issue on GitHub anymore :).
16. Resizeable UI and Text
17. Pagination within Table (Buttons are intuitive, you can also click on the `Page i / n` to input a specific `i` page.
18. Flagging option (hold click at `Filtering OFF/ON` button for two seconds, and hold click at the Flagging Input to see available snippets)
19. Customizable color mapping (function are similar to flagging, but automatically activate at initial load)
20. Revert/Undo and Redo option if mistake happened
21. Field and Imaginary Field with JS and SQLite syntax support, imitating Excel-like formula
22. File scripting using SQLite syntax

# To-Do (next release)

> R = Current release;
> R+1 = Next release;
> R+2 = The one after next;
> R+n = The one after consecutive `n` next;
>
> This is more of a guess, there is a chance I might do R+2 at R+1.

- [x] Remove "Set Target Repo" `(R+1)`
- [x] Auto-save for Filtering & Flagging snippets `(R+1)`
- [x] Add "Add Imaginary Field", similar to "Add Field" but does not affect core .JSON schema, acts like a dummy. Imitating a feature highly similar to Excel formula `(R+1)`
<details>
  <summary>
    See in detail
  </summary>
  <pre>
    1. Add Imaginary Field, is very similar to Add Field, but it does not affect the .JSON schema, instead it creates a clone of the .JSON schema, in addition to the Newly created field, and only add those who already has a value in the Newly created field, so we do not add all existing value and key from original .JSON schema, but we only target those who are necessary. 
    2. For any imaginary field, the column name must be $primary for the text color.
    3. For both "Add field" and "Add imaginary field" input, add .JS support (and SQLite syntax support). This is positioned on the right edge corner of the input. The value may expose any key/column in the row, the whole table. This is also supported for "Edit row" function. 
  </pre>
</details>

- [ ] Add "AI Assistant" for interpretation, creation of custom metric, plot, filtering & flagging snippets, or just for chatting `(R+2)`

<details>
  <summary>
    See in detail
  </summary>
  <pre>
    1. In Setting=>Assistant, there will be three keys:
API_KEY, BASE_URL, MODEL
    2. The given three keys, will be used in the module OpenAI. If something errors, give a toast notification that says what the Error is, if not then do nothing.
    3. If those keys are available now, it will automatically create another section called "Assistant", which act as a chat between the User and the AI. 
    4. In the chat, relevant commands, may be provided "/stat", which will copy all statistics metric of a specific (/stat.COLUMN_NAME) or all key/column (/stat), there's also "/finance" which is like /stat, but for Personal Finance metric. There's also, /table, which will upload the current file we are observing on Table. 
    5. Ensure user able to select, copy any text of the Assistant chat
    6. For the prompt engineering, ensure that the content must be returned in markdown format. 
    7. Whenever the Assistant message has a ``` in the beginning and ``` at the end, convert them to code block, that can be copied
    8. Add a "clear button" that clear the whole chat history, which also mean the AI will forget the whole history.

    Example Usage:
    ```
    Generate comprehensive and easy-to-understand explanation regarding this financial report:
    /finance

    With additional metric such that:
    /stat.price

    Use formal and concise language.
    ```

    That will convert to:
    ```
    Generate comprehensive and easy-to-understand explanation regarding this financial report:
    Net Cash Flow = -272.25
    Savings Rate = 38%
    Expense Ratio = 1:5
    ...

    With additional metric such that:
    Mean = 78.2
    Median = 59.1
    Mode = 74.28
    ...

    Use formal and concise language.
    ```

  </pre>
</details>

- [ ] Automatic Schema Reconcilation via Receipt & Document OCR. A image scanner feature to scan image whether at real-time or assuming there is a given folder, parse the image and apply the operation to the database directly. This is useful, especially when most of your transactions relied on digital banking app, so you do not have to type anymore, but instead the app will do it's job. `(R+3)`
- [ ] Forecasting (ARIMA, SARIMA, MA, SMA), (still in consideration if using XGB, LGBM, or even CB is a applicable in here without sacrificing memory and app size) `(R+2)`
- [ ] Push notifications upon Amending for any instance of warning. For example, let's say I bought Ice Cream for 50 bucks, and my daily budget is 30 bucks, there will be a push notification that warns me about it. `(R+2)`
- [ ] Export report as Document or .pdf. This will also include the plotting. `(R+3)`
- [ ] Minimal, Lightweight, and Accurate voice recognition model that can be triggered by saying the word "Fin", and will listen for any word uttered by the user, and amending what is spoken to the database at a time `(R+4)`
- [ ] Add an extended finance for Investment, Debt, and more. (By theory, you could imitate this in the app right now, without having this feature amended for next release. By categorizing your specific data, say INV (for Investment), create the field necessary, such as Future Value (FV), you could create a custom metric that calculates them) `(R+5)`
- [ ] Improve overall UI, adding icon, and more `(R+2)`
- [ ] Provide databaes alternative, such as Codeberg, or even MongoDB, Supabase, Discord (as a Database), or even Google Drive `(R+5)`

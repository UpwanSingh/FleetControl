package com.fleetcontrol.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleetcontrol.data.entities.CompanyEntity
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.ValidationUtils
import com.fleetcontrol.viewmodel.owner.CompanyViewModel

/**
 * Company Management Screen - Premium Polished Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val companies by viewModel.companies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    
    // Filter companies based on search
    val filteredCompanies = remember(companies, searchQuery) {
        if (searchQuery.isBlank()) companies
        else companies.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Companies", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    navigationIconContentColor = FleetColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
                containerColor = FleetColors.Primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Company", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error display
            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FleetDimens.SpacingMedium),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.ErrorLight)
                ) {
                    Row(
                        modifier = Modifier.padding(FleetDimens.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = FleetColors.Error)
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(it, color = FleetColors.Error)
                    }
                }
            }
            
            if (isLoading && companies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FleetColors.Primary)
                }
            } else if (companies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(FleetColors.SurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = FleetColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                        Text(
                            "No companies yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                        Text(
                            "Tap the + button to add your first company",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(FleetDimens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
                ) {
                    // Search bar
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search companies...", color = FleetColors.TextTertiary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = FleetColors.TextSecondary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FleetColors.TextSecondary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(FleetDimens.CornerLarge),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FleetColors.Primary,
                                unfocusedBorderColor = FleetColors.Border,
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White
                            )
                        )
                    }
                    
                    // Stats Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                            colors = CardDefaults.cardColors(containerColor = FleetColors.Primary),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FleetDimens.SpacingLarge),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${filteredCompanies.size}",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FleetColors.TextOnDark
                                    )
                                    Text(
                                        "Total Companies",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FleetColors.TextOnDarkSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Business,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    items(filteredCompanies) { company ->
                        CompanyCard(
                            company = company,
                            onDelete = { viewModel.deleteCompany(company) }
                        )
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddCompanyDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, rate ->
                viewModel.addCompany(name, rate)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyCard(
    company: CompanyEntity,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(FleetDimens.CornerMedium))
                    .background(FleetColors.InfoLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Business,
                    contentDescription = null,
                    tint = FleetColors.Info
                )
            }
            
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    company.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = FleetColors.TextPrimary
                )
                Text(
                    CurrencyUtils.format(company.perBagRate) + " per bag",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
            
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirm = true
                }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = FleetColors.Error
                )
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = FleetColors.Error) },
            title = { Text("Delete Company?", color = FleetColors.TextPrimary) },
            text = { Text("Are you sure you want to delete ${company.name}?", color = FleetColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = FleetColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AddCompanyDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, rate: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var rateError by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Default.Business, contentDescription = null, tint = FleetColors.Primary) },
        title = { Text("Add Company", color = FleetColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = null
                    },
                    label = { Text("Company Name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = FleetColors.Error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { 
                        rate = it.filter { c -> c.isDigit() || c == '.' }
                        rateError = null
                    },
                    label = { Text("Per Bag Rate (₹)") },
                    singleLine = true,
                    isError = rateError != null,
                    supportingText = rateError?.let { { Text(it, color = FleetColors.Error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameValidation = ValidationUtils.validateCompanyName(name)
                    val rateValidation = ValidationUtils.validateRate(rate)
                    
                    if (!nameValidation.isValid) {
                        nameError = nameValidation.errorMessage
                    }
                    if (!rateValidation.isValid) {
                        rateError = rateValidation.errorMessage
                    }
                    
                    if (nameValidation.isValid && rateValidation.isValid) {
                        onAdd(name, rate.toDoubleOrNull() ?: 0.0)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
            ) {
                Text("Add Company")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.TextSecondary)
            }
        }
    )
}
